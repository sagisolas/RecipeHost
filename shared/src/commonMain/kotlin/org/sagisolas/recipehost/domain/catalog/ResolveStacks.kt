package org.sagisolas.recipehost.domain.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.namespaceOf
import org.sagisolas.recipehost.domain.CATALOG_REFRESH_PERIOD
import org.sagisolas.recipehost.domain.throttleLatest
import kotlin.coroutines.CoroutineContext

/**
 * Resolves `ItemStack`s' item ids against the `Version selection`: each id to the Item in the
 * selected release of its namespace's mod (ADR 0010, 0011). A screen's ids resolve together, in
 * one live query per [CHUNK] ids, which re-runs at most once per [CATALOG_REFRESH_PERIOD].
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class ResolveStacks(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    /** Each of [itemIds] with its resolution. */
    operator fun invoke(itemIds: List<String>): Flow<Map<String, StackResolution>> {
        val ids = itemIds.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())
        return combine(ids.chunked(CHUNK).map(::resolveChunk)) { chunks -> chunks.reduce { all, chunk -> all + chunk } }
    }

    private fun resolveChunk(ids: List<String>): Flow<Map<String, StackResolution>> =
        catalog.itemQueries.selectedOfAll(ids) { modId, releaseId, itemId, displayName, atlasId, cell, columns, cellSize ->
            modId to catalogItemOf(releaseId, itemId, displayName, atlasId, cell, columns, cellSize)
        }
            .asFlow()
            .throttleLatest(CATALOG_REFRESH_PERIOD)
            .mapToList(context)
            .map { rows ->
                val resolved = rows.filter { (modId, item) -> modId == namespaceOf(item.itemId) }.associate { (_, item) -> item.itemId to item }
                ids.associateWith { id -> resolved[id]?.let(StackResolution::Resolved) ?: StackResolution.Unresolved(id) }
            }
}

/** Ids per query: well under SQLite's oldest limit of 999 bound parameters. */
private const val CHUNK = 500

internal sealed interface StackResolution {
    data class Resolved(val item: CatalogItem) : StackResolution

    /**
     * The mod has no release in the `Catalog`, or its selected release lacks the Item: show
     * [itemId] with a placeholder Icon.
     */
    data class Unresolved(val itemId: String) : StackResolution
}
