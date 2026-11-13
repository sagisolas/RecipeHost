/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.scan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.namespaceOf
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Handshake
import org.sagisolas.recipehost.protocol.IconBatch
import org.sagisolas.recipehost.protocol.Item
import org.sagisolas.recipehost.protocol.LayoutBatch
import org.sagisolas.recipehost.protocol.ModInfo
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeBatch
import org.sagisolas.recipehost.protocol.RecipeLayout
import org.sagisolas.recipehost.protocol.RecipeType
import org.sagisolas.recipehost.protocol.Slot
import kotlin.time.Clock

/**
 * Writes one `Scan`'s `Snapshot` into the `Catalog` (ADR 0010, 0011).
 *
 * Items are staged in memory and Recipes in a [RecipeSpool] file (ADR 0017), and both are
 * committed in one transaction after the last RecipeBatch, so a stream that fails before then
 * writes nothing. Icons are upserted batch by batch after that commit, one `Icon atlas` per
 * IconBatch, so a drop during Icons keeps what was committed. `Recipe layout`s follow the same
 * pattern: each LayoutBatch is stored and linked to its recipes in its own transaction after the
 * commit, and once the Scan ends, normally or by a drop, layouts no recipe points at are deleted.
 */
internal class ScanIngest(
    private val catalog: CatalogDatabase,
    private val clock: Clock,
    private val spool: RecipeSpool,
) {
    /**
     * Emits [ScanProgress.Connecting] first and ends with [ScanProgress.Done] or
     * [ScanProgress.Failed]; it never throws (except cancellation).
     *
     * The commit runs once the staged recipes reach the `Handshake`'s total. The wire doesn't
     * mark the last RecipeBatch, so the first IconBatch or the end of the stream also commits
     * (a Scan with no recipes, or a mod that sent fewer than its total). More recipes than the
     * total fails the Scan before anything is written.
     *
     * A retry after a drop during Icons starts over with a new `Handshake`, so progress goes back
     * to Items and the Scan commits again; the commit replaces what the failed attempt wrote.
     */
    fun ingest(envelopes: Flow<Envelope>): Flow<ScanProgress> =
        flow {
            emit(ScanProgress.Connecting)
            var scan: StagedScan? = null

            suspend fun FlowCollector<ScanProgress>.commitOnce(staged: StagedScan): Committed {
                staged.committed?.let { return it }
                return commit(staged).also {
                    staged.committed = it
                    discardSpool()
                    emit(ScanProgress.Committed)
                }
            }
            envelopes.collect { envelope ->
                // A retry (RetryingSessionClient) restarts with a new Handshake: drop what was staged.
                envelope.handshake?.let {
                    scan = StagedScan(it)
                    spool.begin()
                }
                envelope.item_batch?.let { batch ->
                    val staged = scan.stillStaging("ItemBatch")
                    staged.items += batch.items
                    emit(ScanProgress.Items(staged.items.size, staged.handshake.total_items))
                }
                envelope.recipe_batch?.let { batch ->
                    val staged = scan.stillStaging("RecipeBatch")
                    // Checked on arrival: the recipes are spooled, so the commit can't check them before it opens.
                    batch.requireValid(staged.modIds)
                    val received = staged.recipesReceived + batch.recipes.size
                    val total = staged.handshake.total_recipes
                    if (received > total) throw RecipeCountExceededException(received, total)
                    spool.append(batch)
                    staged.recipesReceived = received
                    emit(ScanProgress.Recipes(received, total))
                    if (received == total) commitOnce(staged)
                }
                envelope.icon_batch?.let { batch ->
                    val staged = scan ?: error("IconBatch before Handshake")
                    storeAtlasAndPruneOrphans(commitOnce(staged), batch)
                    staged.iconsReceived += batch.item_ids.size
                    // The Handshake has no Icon total; the mod sends one Icon per Item.
                    emit(ScanProgress.Icons(staged.iconsReceived, staged.handshake.total_items))
                }
                envelope.layout_batch?.let { batch ->
                    val staged = scan ?: error("LayoutBatch before Handshake")
                    staged.layoutRecipesReceived += storeLayouts(commitOnce(staged), batch)
                    emit(ScanProgress.Layouts(staged.layoutRecipesReceived, staged.handshake.total_recipes))
                }
            }
            val staged = scan ?: error("stream ended before Handshake")
            commitOnce(staged)
            deleteUnreferenced()
            emit(ScanProgress.Done)
        }.onCompletion {
            // Normally gone after the commit; this covers a Scan that failed or was cancelled before it.
            discardSpool()
        }.catch { cause ->
            if (cause is CancellationException) throw cause
            // The screen only shows a reason; the cause is only ever seen here (logcat / Xcode console).
            println("[recipehost] Scan failed: ${cause.stackTraceToString()}")
            // A drop keeps the Icons and layouts that arrived, but they may still have orphaned old ones.
            runCatching { deleteUnreferenced() }
            emit(ScanProgress.Failed(cause))
        }

    /**
     * Deletes the spool file. A failure only leaves a temp file for the next Scan to overwrite, so
     * it never fails a Scan whose recipes are already committed.
     */
    private fun discardSpool() {
        runCatching { spool.delete() }.onFailure { println("[recipehost] Couldn't delete the recipe spool: $it") }
    }

    /**
     * Deletes the layouts no recipe points at and the atlases no Icon points at. Run once the Scan
     * has ended: its commit unlinked the recipes it replaced, and its IconBatches repointed Icons,
     * so their layouts and atlases may be orphans now. A full scan of each table, so once per Scan
     * rather than per batch.
     */
    private fun deleteUnreferenced() =
        catalog.transaction {
            catalog.recipeLayoutQueries.deleteUnreferenced()
            catalog.iconQueries.deleteUnreferencedAtlases()
        }

    /** Writes the staged Items and Recipes in one transaction. */
    private fun commit(scan: StagedScan): Committed {
        val handshake = scan.handshake
        // Items are checked before the transaction opens, and Recipes as they arrived, so a broken
        // mod contract writes nothing.
        scan.items.forEach { requireModInfo(scan.modIds, it.id) }
        val now = clock.now().toEpochMilliseconds()
        return catalog.transactionWithResult {
            val releaseIds =
                handshake.mods.withIndex().associate { (position, mod) ->
                    mod.id to upsertRelease(mod, handshake.minecraft_version, position.toLong(), now)
                }
            val instanceId = upsertInstance(handshake.instance_name, now)
            scan.items.forEachIndexed { ordinal, item ->
                upsertItem(releaseIds.releaseOf(item.id), item, ordinal.toLong())
            }
            spool.read { batches -> replaceRecipes(instanceId, releaseIds, batches.flatMap { it.expandedRecipes() }) }
            upsertTypeNames(releaseIds, handshake.recipe_types)
            releaseIds.forEach { (modId, releaseId) -> catalog.versionSelectionQueries.choose(modId, releaseId) }
            Committed(releaseIds, instanceId)
        }
    }

    /**
     * Stores the IconBatch's `Icon atlas` and points each of its items' Icons at their cell (ADR 0014).
     * One short transaction per batch, so each committed batch survives a later drop. Atlases no
     * Icon points at any more are deleted once the Scan ends.
     */
    private fun storeAtlasAndPruneOrphans(
        committed: Committed,
        batch: IconBatch,
    ) {
        if (batch.item_ids.isEmpty()) return
        if (batch.columns <= 0 || batch.cell_size <= 0) throw InvalidIconAtlasException(batch.columns, batch.cell_size)
        // Checked before the transaction opens, so a broken batch writes nothing.
        val releases = batch.item_ids.map { committed.releaseOf(it) }
        catalog.transaction {
            with(catalog.iconQueries) {
                insertAtlas(batch.png.toByteArray(), batch.columns.toLong(), batch.cell_size.toLong())
                val atlasId = lastInsertedAtlasId().executeAsOne()
                batch.item_ids.forEachIndexed { cell, itemId -> upsert(releases[cell], itemId, atlasId, cell.toLong()) }
            }
        }
    }

    /**
     * Stores each layout of the batch that carries an image (replacing what an earlier Scan stored
     * under its key) and points the listed recipes of this Scan's Instance at it, each under its
     * id's release. One transaction per batch, like Icons. Returns how many recipes it linked.
     */
    private fun storeLayouts(
        committed: Committed,
        batch: LayoutBatch,
    ): Int {
        // Checked before the transaction opens, so a broken batch writes nothing.
        val links =
            batch.layouts.flatMap { layout ->
                layout.recipe_ids.map { recipeId -> LayoutLink(layout.key, committed.releaseOf(recipeId), recipeId) }
            }
        catalog.transaction {
            batch.layouts.filter { it.png.size > 0 }.forEach { layout ->
                catalog.recipeLayoutQueries.upsert(
                    key = layout.key,
                    png = layout.png.toByteArray(),
                    width = layout.width.toLong(),
                    height = layout.height.toLong(),
                    slots = RecipeLayout.ADAPTER.encode(RecipeLayout(slots = layout.slots)),
                )
            }
            links.forEach { link ->
                catalog.recipeQueries.linkLayout(link.layoutKey, committed.instanceId, link.releaseId, link.recipeId)
            }
        }
        return links.size
    }

    private fun upsertRelease(
        mod: ModInfo,
        mcVersion: String,
        position: Long,
        now: Long,
    ): Long =
        with(catalog.modReleaseQueries) {
            insertIfAbsent(mod.id, mod.version, mcVersion, mod.name, position, now)
            markScanned(mod.name, position, now, mod.id, mod.version, mcVersion)
            idOf(mod.id, mod.version, mcVersion).executeAsOne()
        }

    private fun upsertInstance(
        name: String,
        now: Long,
    ): Long =
        with(catalog.instanceQueries) {
            insertIfAbsent(name, now)
            markScanned(now, name)
            idOf(name).executeAsOne()
        }

    private fun upsertItem(
        releaseId: Long,
        item: Item,
        ordinal: Long,
    ) = with(catalog.itemQueries) {
        update(item.display_name, ordinal, releaseId, item.id)
        insertIfAbsent(releaseId, item.id, item.display_name, ordinal)
    }

    /**
     * Names each type under its namespace's release. A type whose namespace has no release in this
     * Scan (an EMI category of a mod that owns no Items or Recipes) has nowhere to go and stays unnamed.
     */
    private fun upsertTypeNames(
        releaseIds: Map<String, Long>,
        types: List<RecipeType>,
    ) = types.forEach { type ->
        releaseIds[namespaceOf(type.id)]?.let { catalog.recipeTypeQueries.upsert(it, type.id, type.name) }
    }

    /** Replaces this Instance's recipes for every release in the Scan; each recipe goes to its id's namespace. */
    private fun replaceRecipes(
        instanceId: Long,
        releaseIds: Map<String, Long>,
        recipes: Sequence<Recipe>,
    ) = with(catalog.recipeQueries) {
        releaseIds.values.forEach { releaseId ->
            deleteOutputsOf(instanceId, releaseId)
            deleteInputsOf(instanceId, releaseId)
            deleteOf(instanceId, releaseId)
        }
        var nextPk: Long? = null
        recipes.forEach { recipe ->
            val body = Recipe.ADAPTER.encodeByteString(recipe)
            val releaseId = releaseIds.releaseOf(recipe.id)
            val bodyHash = body.sha256().toByteArray()
            val recipePk =
                nextPk?.also { pk ->
                    insertWithPk(pk, releaseId, recipe.id, instanceId, recipe.type_id, body.toByteArray(), bodyHash)
                } ?: run {
                    insert(releaseId, recipe.id, instanceId, recipe.type_id, body.toByteArray(), bodyHash)
                    lastInsertedId().executeAsOne()
                }
            recipe.results.mapTo(HashSet()) { it.item_id }.forEach { insertOutput(recipePk, it) }
            // An item in several Slots, or twice in one, is one row.
            recipe.inputs
                .flatMapTo(
                    HashSet(),
                ) { slot -> slot.stacks.map { it.item_id } }
                .forEach { insertInput(recipePk, it) }
            nextPk = recipePk + 1
        }
    }

    private class StagedScan(
        val handshake: Handshake,
    ) {
        val modIds: Set<String> = handshake.mods.mapTo(HashSet()) { it.id }
        val items = ArrayList<Item>()

        /** Recipes appended to the spool so far. */
        var recipesReceived = 0

        /** Set by the commit: where Icons and layouts are filed. */
        var committed: Committed? = null
        var iconsReceived = 0
        var layoutRecipesReceived = 0
    }

    private fun StagedScan?.stillStaging(what: String): StagedScan {
        val staged = this ?: error("$what before Handshake")
        check(
            staged.committed == null,
        ) { "$what after the Scan's ${staged.handshake.total_recipes} recipes were committed" }
        return staged
    }

    /** What the commit wrote: the release id per mod id, and the Scan's Instance. */
    private class Committed(
        val releaseIds: Map<String, Long>,
        val instanceId: Long,
    ) {
        fun releaseOf(id: String): Long = releaseIds.releaseOf(id)
    }

    /** One recipe of this Scan's Instance that a layout lists, under its id's release. */
    private class LayoutLink(
        val layoutKey: String,
        val releaseId: Long,
        val recipeId: String,
    )
}

private fun requireModInfo(
    modIds: Set<String>,
    id: String,
) {
    val namespace = namespaceOf(id)
    if (namespace !in modIds) throw UnknownNamespaceException(namespace, id)
}

/** The release that owns [id]: its namespace's (ADR 0011). */
private fun Map<String, Long>.releaseOf(id: String): Long {
    val namespace = namespaceOf(id)
    return this[namespace] ?: throw UnknownNamespaceException(namespace, id)
}

/**
 * Fails the way the commit's expansion and filing would, without expanding: a recipe whose
 * namespace has no `ModInfo`, or a Slot pointing past the batch's `shared_slots`.
 */
private fun RecipeBatch.requireValid(modIds: Set<String>) =
    recipes.forEach { recipe ->
        requireModInfo(modIds, recipe.id)
        (recipe.inputs.asSequence() + recipe.layout_stacks).forEach { slot ->
            if (slot.shared > shared_slots.size) throw InvalidSharedSlotException(slot.shared, shared_slots.size)
        }
    }

/**
 * The companion mod broke its contract (issue 01): it sent an Item, Recipe or Icon, or a layout
 * listing a recipe, whose id's namespace has no `ModInfo` in the `Handshake`, so there is no
 * `Mod release` to file it under.
 */
internal class UnknownNamespaceException(
    val namespace: String,
    val id: String,
) : IllegalStateException("no ModInfo in the Handshake for namespace '$namespace' of $id")

/** A Slot pointing past its RecipeBatch's `shared_slots` (ADR 0016). */
internal class InvalidSharedSlotException(
    val shared: Int,
    val tableSize: Int,
) : IllegalStateException("Slot refers to shared slot $shared, but its RecipeBatch shares $tableSize")

/** The batch's recipes with each shared Slot replaced by the Slot it stands for (ADR 0016). */
private fun RecipeBatch.expandedRecipes(): List<Recipe> {
    fun Slot.expanded(): Slot =
        if (shared ==
            0
        ) {
            this
        } else {
            shared_slots.getOrNull(shared - 1)
                ?: throw InvalidSharedSlotException(shared, shared_slots.size)
        }
    return recipes.map { recipe ->
        recipe.copy(
            inputs = recipe.inputs.map { it.expanded() },
            layout_stacks = recipe.layout_stacks.map { it.expanded() },
        )
    }
}

/** An IconBatch whose geometry can't locate its cells. */
internal class InvalidIconAtlasException(
    val columns: Int,
    val cellSize: Int,
) : IllegalStateException("IconBatch with $columns columns of $cellSize px cells")

/** The mod sent more recipes than its `Handshake` announced, so the commit point can't be trusted. */
internal class RecipeCountExceededException(
    val received: Int,
    val total: Int,
) : IllegalStateException("received $received recipes, but the Handshake announced $total")
