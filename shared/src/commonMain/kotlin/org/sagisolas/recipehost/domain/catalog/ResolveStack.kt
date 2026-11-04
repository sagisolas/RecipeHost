package org.sagisolas.recipehost.domain.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.ByteString.Companion.toByteString
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.namespaceOf
import kotlin.coroutines.CoroutineContext

/**
 * Resolves an `ItemStack`'s item id against the `Version selection`: the Item in the selected
 * release of the id's namespace's mod (ADR 0010, 0011).
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class ResolveStack(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    operator fun invoke(itemId: String): Flow<StackResolution> =
        catalog.itemQueries.selectedOf(namespaceOf(itemId), itemId) { releaseId, id, displayName, png ->
            CatalogItem(releaseId, id, displayName, png?.toByteString())
        }.asFlow().mapToOneOrNull(context).map { item ->
            if (item != null) StackResolution.Resolved(item) else StackResolution.Unresolved(itemId)
        }
}

internal sealed interface StackResolution {
    data class Resolved(val item: CatalogItem) : StackResolution

    /**
     * The mod has no release in the `Catalog`, or its selected release lacks the Item: show
     * [itemId] with a placeholder Icon.
     */
    data class Unresolved(val itemId: String) : StackResolution
}
