package org.sagisolas.recipehost.data.scan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.namespaceOf
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.Handshake
import org.sagisolas.recipehost.protocol.IconBatch
import org.sagisolas.recipehost.protocol.Item
import org.sagisolas.recipehost.protocol.LayoutBatch
import org.sagisolas.recipehost.protocol.ModInfo
import org.sagisolas.recipehost.protocol.Recipe
import org.sagisolas.recipehost.protocol.RecipeLayout
import kotlin.time.Clock

/**
 * Writes one `Scan`'s `Snapshot` into the `Catalog` (ADR 0010, 0011).
 *
 * Items and Recipes are staged in memory and committed in one transaction after the last
 * RecipeBatch, so a stream that fails before then writes nothing. Icons are upserted batch by
 * batch after that commit, one `Icon atlas` per IconBatch, so a drop during Icons keeps what
 * was committed. `Recipe layout`s follow the same pattern: each LayoutBatch is stored and linked
 * to its recipes in its own transaction after the commit, and once the Scan ends, normally or by a
 * drop, layouts no recipe points at are deleted.
 */
internal class ScanIngest(private val catalog: CatalogDatabase, private val clock: Clock) {

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
    fun ingest(envelopes: Flow<Envelope>): Flow<ScanProgress> = flow {
        emit(ScanProgress.Connecting)
        var scan: StagedScan? = null
        suspend fun FlowCollector<ScanProgress>.commitOnce(staged: StagedScan): Committed {
            staged.committed?.let { return it }
            return commit(staged).also {
                staged.committed = it
                emit(ScanProgress.Committed)
            }
        }
        envelopes.collect { envelope ->
            // A retry (RetryingSessionClient) restarts with a new Handshake: drop what was staged.
            envelope.handshake?.let { scan = StagedScan(it) }
            envelope.item_batch?.let { batch ->
                val staged = scan.stillStaging("ItemBatch")
                staged.items += batch.items
                emit(ScanProgress.Items(staged.items.size, staged.handshake.total_items))
            }
            envelope.recipe_batch?.let { batch ->
                val staged = scan.stillStaging("RecipeBatch")
                staged.recipes += batch.recipes
                val total = staged.handshake.total_recipes
                if (staged.recipes.size > total) throw RecipeCountExceededException(staged.recipes.size, total)
                emit(ScanProgress.Recipes(staged.recipes.size, total))
                if (staged.recipes.size == total) commitOnce(staged)
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
                storeLayouts(commitOnce(staged), batch)
            }
        }
        val staged = scan ?: error("stream ended before Handshake")
        commitOnce(staged)
        deleteUnreferencedLayouts()
        emit(ScanProgress.Done)
    }.catch { cause ->
        if (cause is CancellationException) throw cause
        // A drop keeps the layouts that arrived, but its commit may still have orphaned old ones.
        runCatching { deleteUnreferencedLayouts() }
        emit(ScanProgress.Failed(cause))
    }

    /**
     * Deletes the layouts no recipe points at. Run once the Scan has ended: its commit unlinked the
     * recipes it replaced, and their layouts may be orphans now.
     */
    private fun deleteUnreferencedLayouts() = catalog.recipeLayoutQueries.deleteUnreferenced()

    /** Writes the staged Items and Recipes in one transaction. */
    private fun commit(scan: StagedScan): Committed {
        val handshake = scan.handshake
        val modIds = handshake.mods.mapTo(HashSet()) { it.id }
        // Checked before the transaction opens, so a broken mod contract writes nothing.
        scan.items.forEach { requireModInfo(modIds, it.id) }
        scan.recipes.forEach { requireModInfo(modIds, it.id) }
        val now = clock.now().toEpochMilliseconds()
        return catalog.transactionWithResult {
            val releaseIds = handshake.mods.withIndex().associate { (position, mod) ->
                mod.id to upsertRelease(mod, handshake.minecraft_version, position.toLong(), now)
            }
            val instanceId = upsertInstance(handshake.instance_name, now)
            scan.items.forEachIndexed { ordinal, item ->
                upsertItem(releaseIds.releaseOf(item.id), item, ordinal.toLong())
            }
            replaceRecipes(instanceId, releaseIds, scan.recipes)
            releaseIds.forEach { (modId, releaseId) -> catalog.versionSelectionQueries.choose(modId, releaseId) }
            Committed(releaseIds, instanceId)
        }
    }

    /**
     * Stores the IconBatch's `Icon atlas` and points each of its items' Icons at their cell, then
     * deletes the atlases no Icon points at any more (ADR 0014). One short transaction per batch,
     * so each committed batch survives a later drop.
     */
    private fun storeAtlasAndPruneOrphans(committed: Committed, batch: IconBatch) {
        if (batch.item_ids.isEmpty()) return
        if (batch.columns <= 0 || batch.cell_size <= 0) throw InvalidIconAtlasException(batch.columns, batch.cell_size)
        // Checked before the transaction opens, so a broken batch writes nothing.
        val releases = batch.item_ids.map { committed.releaseOf(it) }
        catalog.transaction {
            with(catalog.iconQueries) {
                insertAtlas(batch.png.toByteArray(), batch.columns.toLong(), batch.cell_size.toLong())
                val atlasId = lastInsertedAtlasId().executeAsOne()
                batch.item_ids.forEachIndexed { cell, itemId -> upsert(releases[cell], itemId, atlasId, cell.toLong()) }
                deleteUnreferencedAtlases()
            }
        }
    }

    /**
     * Stores each layout of the batch that carries an image (replacing what an earlier Scan stored
     * under its key) and points the listed recipes of this Scan's Instance at it, each under its
     * id's release. One transaction per batch, like Icons.
     */
    private fun storeLayouts(committed: Committed, batch: LayoutBatch) {
        // Checked before the transaction opens, so a broken batch writes nothing.
        val links = batch.layouts.flatMap { layout ->
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
    }

    private fun upsertRelease(mod: ModInfo, mcVersion: String, position: Long, now: Long): Long = with(catalog.modReleaseQueries) {
        insertIfAbsent(mod.id, mod.version, mcVersion, mod.name, position, now)
        markScanned(mod.name, position, now, mod.id, mod.version, mcVersion)
        idOf(mod.id, mod.version, mcVersion).executeAsOne()
    }

    private fun upsertInstance(name: String, now: Long): Long = with(catalog.instanceQueries) {
        insertIfAbsent(name, now)
        markScanned(now, name)
        idOf(name).executeAsOne()
    }

    private fun upsertItem(releaseId: Long, item: Item, ordinal: Long) = with(catalog.itemQueries) {
        update(item.display_name, ordinal, releaseId, item.id)
        insertIfAbsent(releaseId, item.id, item.display_name, ordinal)
    }

    /** Replaces this Instance's recipes for every release in the Scan; each recipe goes to its id's namespace. */
    private fun replaceRecipes(instanceId: Long, releaseIds: Map<String, Long>, recipes: List<Recipe>) =
        with(catalog.recipeQueries) {
            releaseIds.values.forEach { releaseId ->
                deleteOutputsOf(instanceId, releaseId)
                deleteInputsOf(instanceId, releaseId)
                deleteOf(instanceId, releaseId)
            }
            recipes.forEach { recipe ->
                val body = Recipe.ADAPTER.encodeByteString(recipe)
                insert(
                    releaseId = releaseIds.releaseOf(recipe.id),
                    recipeId = recipe.id,
                    instanceId = instanceId,
                    typeId = recipe.type_id,
                    body = body.toByteArray(),
                    bodyHash = body.sha256().toByteArray(),
                )
                val recipePk = lastInsertedId().executeAsOne()
                recipe.results.forEach { insertOutput(recipePk, it.item_id) }
                recipe.inputs.forEach { slot -> slot.stacks.forEach { insertInput(recipePk, it.item_id) } }
            }
        }

    private class StagedScan(val handshake: Handshake) {
        // TODO: measure memory and commit time on a large modpack; move to staging tables if needed.
        val items = ArrayList<Item>()
        val recipes = ArrayList<Recipe>()
        /** Set by the commit: where Icons and layouts are filed. */
        var committed: Committed? = null
        var iconsReceived = 0
    }

    private fun StagedScan?.stillStaging(what: String): StagedScan {
        val staged = this ?: error("$what before Handshake")
        check(staged.committed == null) { "$what after the Scan's ${staged.handshake.total_recipes} recipes were committed" }
        return staged
    }

    /** What the commit wrote: the release id per mod id, and the Scan's Instance. */
    private class Committed(val releaseIds: Map<String, Long>, val instanceId: Long) {
        fun releaseOf(id: String): Long = releaseIds.releaseOf(id)
    }

    /** One recipe of this Scan's Instance that a layout lists, under its id's release. */
    private class LayoutLink(val layoutKey: String, val releaseId: Long, val recipeId: String)

    private companion object {
        fun requireModInfo(modIds: Set<String>, id: String) {
            val namespace = namespaceOf(id)
            if (namespace !in modIds) throw UnknownNamespaceException(namespace, id)
        }

        /** The release that owns [id]: its namespace's (ADR 0011). */
        fun Map<String, Long>.releaseOf(id: String): Long {
            val namespace = namespaceOf(id)
            return this[namespace] ?: throw UnknownNamespaceException(namespace, id)
        }
    }
}

/**
 * The companion mod broke its contract (issue 01): it sent an Item, Recipe or Icon, or a layout
 * listing a recipe, whose id's namespace has no `ModInfo` in the `Handshake`, so there is no
 * `Mod release` to file it under.
 */
internal class UnknownNamespaceException(val namespace: String, val id: String) :
    IllegalStateException("no ModInfo in the Handshake for namespace '$namespace' of $id")

/** An IconBatch whose geometry can't locate its cells. */
internal class InvalidIconAtlasException(val columns: Int, val cellSize: Int) :
    IllegalStateException("IconBatch with $columns columns of $cellSize px cells")

/** The mod sent more recipes than its `Handshake` announced, so the commit point can't be trusted. */
internal class RecipeCountExceededException(val received: Int, val total: Int) :
    IllegalStateException("received $received recipes, but the Handshake announced $total")
