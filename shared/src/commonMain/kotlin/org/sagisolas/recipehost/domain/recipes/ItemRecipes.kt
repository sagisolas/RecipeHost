/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.recipes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.data.catalog.MINECRAFT
import org.sagisolas.recipehost.data.catalog.namespaceOf
import org.sagisolas.recipehost.domain.CATALOG_REFRESH_PERIOD
import org.sagisolas.recipehost.domain.throttleLatest
import kotlin.coroutines.CoroutineContext

/** Which recipes of an Item: those that make it, or those that use it. */
internal enum class RecipeDirection {
    /** The recipes with the Item among their results. */
    Recipes,

    /** The recipes with the Item among the alternatives of an input `Slot`. */
    Uses,
}

/**
 * An Item's recipes or uses among those owned by the `Version selection` (ADR 0011), grouped by
 * type, each with its `Instance` variants decoded into how they are drawn. The flow re-emits when
 * the `Catalog` changes, at most once per [CATALOG_REFRESH_PERIOD].
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class ItemRecipes(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    operator fun invoke(
        itemId: String,
        direction: RecipeDirection,
    ): Flow<List<RecipeGroup>> {
        val query =
            when (direction) {
                RecipeDirection.Recipes -> catalog.recipeQueries.selectedProducing(itemId, ::InstanceRecipe)
                RecipeDirection.Uses -> catalog.recipeQueries.selectedConsuming(itemId, ::InstanceRecipe)
            }
        return query
            .asFlow()
            .throttleLatest(CATALOG_REFRESH_PERIOD)
            .mapToList(context)
            .map { it.toGroups() }
    }
}

/**
 * Groups rows by type, then collapses each `(release, recipe id)` by body hash into variants.
 * `minecraft:` types come first, then the others; each part keeps the type order of the rows.
 * Expects the rows ordered by type, recipe id and release, newest Instance first within a recipe,
 * as the recipe queries return them.
 */
private fun List<InstanceRecipe>.toGroups(): List<RecipeGroup> =
    groupBy { it.typeId }
        .map { (typeId, rows) ->
            RecipeGroup(
                typeId = typeId,
                name = rows.first().typeName,
                recipes =
                    rows.groupBy { it.releaseId to it.recipeId }.map { (key, instances) ->
                        CatalogRecipe(releaseId = key.first, recipeId = key.second, variants = instances.toVariants())
                    },
            )
        }.sortedBy { namespaceOf(it.typeId) != MINECRAFT } // stable: false (the game's own) sorts first
