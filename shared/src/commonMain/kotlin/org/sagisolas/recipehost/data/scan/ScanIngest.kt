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
import org.sagisolas.recipehost.protocol.Icon
import org.sagisolas.recipehost.protocol.Item
import org.sagisolas.recipehost.protocol.ModInfo
import org.sagisolas.recipehost.protocol.Recipe
import kotlin.time.Clock

/**
 * Writes one `Scan`'s `Snapshot` into the `Catalog` (ADR 0010, 0011).
 *
 * Items and Recipes are staged in memory and committed in one transaction after the last
 * RecipeBatch, so a stream that fails before then writes nothing. Icons are upserted batch by
 * batch after that commit, so a drop during Icons keeps what was committed.
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
        suspend fun FlowCollector<ScanProgress>.commitOnce(staged: StagedScan) {
            if (staged.releaseIds != null) return
            staged.releaseIds = commit(staged)
            emit(ScanProgress.Committed)
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
                commitOnce(staged)
                upsertIcons(staged.releaseIds!!, batch.icons)
                staged.iconsReceived += batch.icons.size
                // The Handshake has no Icon total; the mod sends one Icon per Item.
                emit(ScanProgress.Icons(staged.iconsReceived, staged.handshake.total_items))
            }
        }
        val staged = scan ?: error("stream ended before Handshake")
        commitOnce(staged)
        emit(ScanProgress.Done)
    }.catch { cause ->
        if (cause is CancellationException) throw cause
        emit(ScanProgress.Failed(cause))
    }

    /** Writes the staged Items and Recipes in one transaction; returns the release id per mod id. */
    private fun commit(scan: StagedScan): Map<String, Long> {
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
            releaseIds
        }
    }

    /** One short transaction per IconBatch, so each committed batch survives a later drop. */
    private fun upsertIcons(releaseIds: Map<String, Long>, icons: List<Icon>) = catalog.transaction {
        icons.forEach { icon ->
            catalog.iconQueries.upsert(releaseIds.releaseOf(icon.item_id), icon.item_id, icon.png.toByteArray())
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
        /** Set by the commit: the release id per mod id, which Icons are filed under. */
        var releaseIds: Map<String, Long>? = null
        var iconsReceived = 0
    }

    private fun StagedScan?.stillStaging(what: String): StagedScan {
        val staged = this ?: error("$what before Handshake")
        check(staged.releaseIds == null) { "$what after the Scan's ${staged.handshake.total_recipes} recipes were committed" }
        return staged
    }

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
 * The companion mod broke its contract (issue 01): it sent an Item, Recipe or Icon whose id's
 * namespace has no `ModInfo` in the `Handshake`, so there is no `Mod release` to file it under.
 */
internal class UnknownNamespaceException(val namespace: String, val id: String) :
    IllegalStateException("no ModInfo in the Handshake for namespace '$namespace' of $id")

/** The mod sent more recipes than its `Handshake` announced, so the commit point can't be trusted. */
internal class RecipeCountExceededException(val received: Int, val total: Int) :
    IllegalStateException("received $received recipes, but the Handshake announced $total")
