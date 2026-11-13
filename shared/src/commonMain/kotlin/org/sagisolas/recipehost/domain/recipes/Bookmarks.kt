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
import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import org.sagisolas.recipehost.domain.CATALOG_REFRESH_PERIOD
import org.sagisolas.recipehost.domain.throttleLatest
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * A bookmarked recipe as the `Version selection` has it: its [typeId] with the type's [typeName]
 * (null when the `Catalog` has none), and its distinct bodies across `Instance`s, newest first.
 */
internal data class BookmarkedRecipe(
    val recipeId: String,
    val typeId: String,
    val typeName: String?,
    val variants: List<RecipeVariant>,
)

/**
 * The user's `Bookmark`s: recipe ids saved for quick access, kept until the `Catalog` is cleared.
 * A bookmark shows its recipe under the `Version selection`, and is hidden while no selected
 * release has it. The flows re-emit when the `Catalog` changes, at most once per
 * [CATALOG_REFRESH_PERIOD].
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher. [clock] orders the bookmarks.
 */
internal class Bookmarks(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
    private val clock: Clock,
) {
    /** The bookmarked recipe ids, whether or not they resolve. */
    val ids: Flow<Set<String>> =
        catalog.bookmarkQueries
            .ids()
            .asFlow()
            .mapToList(context)
            .map { it.toSet() }

    /** The bookmarked recipes that resolve, newest bookmark first. */
    val recipes: Flow<List<BookmarkedRecipe>> =
        catalog.bookmarkQueries
            .selectedBookmarked(::InstanceRecipe)
            .asFlow()
            .throttleLatest(CATALOG_REFRESH_PERIOD)
            .mapToList(context)
            .map { it.toBookmarked() }

    /** Bookmarks [recipeId], or removes its bookmark if it has one. */
    suspend fun toggle(recipeId: String) =
        withContext(context) {
            catalog.transaction {
                val queries = catalog.bookmarkQueries
                if (queries.isBookmarked(recipeId).executeAsOne() > 0) {
                    queries.delete(recipeId)
                } else {
                    queries.insert(recipeId, clock.now().toEpochMilliseconds())
                }
            }
        }

    suspend fun remove(recipeId: String) = withContext(context) { catalog.bookmarkQueries.delete(recipeId) }

    /**
     * Whether no bookmark resolves, on the calling thread: `AppGraph` asks it on the main thread,
     * only on a fresh start, to choose the start tab.
     */
    fun isEmpty(): Boolean = catalog.bookmarkQueries.resolvedCount().executeAsOne() == 0L
}

/** Rows ordered by bookmark, newest Instance first within a recipe; groupBy keeps first-seen order. */
private fun List<InstanceRecipe>.toBookmarked(): List<BookmarkedRecipe> =
    groupBy { it.recipeId }.map { (recipeId, rows) ->
        BookmarkedRecipe(
            recipeId = recipeId,
            typeId = rows.first().typeId,
            typeName = rows.first().typeName,
            variants = rows.toVariants(),
        )
    }
