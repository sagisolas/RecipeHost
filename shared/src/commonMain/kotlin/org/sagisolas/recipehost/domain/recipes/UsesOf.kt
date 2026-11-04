package org.sagisolas.recipehost.domain.recipes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import kotlin.coroutines.CoroutineContext

/**
 * The recipes that use an Item: those owned by the `Version selection` with the item among the
 * alternatives of an input `Slot`, grouped by type, with their `Instance` variants (ADR 0011).
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class UsesOf(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    operator fun invoke(itemId: String): Flow<List<RecipeGroup>> =
        catalog.recipeQueries.selectedConsuming(itemId, ::InstanceRecipe).asFlow().mapToList(context).map { it.toGroups() }
}
