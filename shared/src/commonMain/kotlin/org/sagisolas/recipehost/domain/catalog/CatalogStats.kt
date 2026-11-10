package org.sagisolas.recipehost.domain.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import kotlin.coroutines.CoroutineContext

/**
 * How much the `Catalog` holds; emits again whenever a `Scan` or a clear changes it.
 *
 * [context] runs the query; `AppGraph` passes an I/O dispatcher.
 */
internal class CatalogStats(
    catalog: CatalogDatabase,
    context: CoroutineContext,
) {
    val counts: Flow<CatalogCounts> =
        catalog.catalogStatsQueries.counts().asFlow().mapToOne(context).map {
            CatalogCounts(mods = it.mods, items = it.items, recipes = it.recipes)
        }
}

/**
 * [mods] counts `Mod release`s, so each version of a mod counts; [recipes] counts a recipe kept
 * for several `Instance`s once.
 */
internal data class CatalogCounts(
    val mods: Long,
    val items: Long,
    val recipes: Long,
)
