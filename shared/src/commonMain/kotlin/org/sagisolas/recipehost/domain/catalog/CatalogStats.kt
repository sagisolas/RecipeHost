package org.sagisolas.recipehost.domain.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import kotlin.coroutines.CoroutineContext

/**
 * How much the `Catalog` holds; emits again whenever a `Scan` or a clear changes it.
 *
 * [driver] is [catalog]'s, for the size pragmas. [context] runs the queries; `AppGraph` passes an
 * I/O dispatcher.
 */
internal class CatalogStats(
    catalog: CatalogDatabase,
    private val driver: SqlDriver,
    private val context: CoroutineContext,
) {
    val counts: Flow<CatalogCounts> =
        catalog.catalogStatsQueries.counts().asFlow().mapToOne(context).map {
            CatalogCounts(mods = it.mods, items = it.items, recipes = it.recipes, bytes = usedBytes())
        }

    // Through the driver rather than a .sq query: table-valued pragma functions need SQLite 3.16,
    // newer than Android 7's. Free pages are left out, so a clear shrinks it without a VACUUM.
    private suspend fun usedBytes() =
        withContext(context) {
            (pragma("page_count") - pragma("freelist_count")) * pragma("page_size")
        }

    private fun pragma(name: String): Long =
        driver
            .executeQuery(null, "PRAGMA $name", { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            }, 0)
            .value
}

/**
 * [mods] counts `Mod release`s, so each version of a mod counts; [recipes] counts a recipe kept
 * for several `Instance`s once. [bytes] is how much of the database file holds data.
 */
internal data class CatalogCounts(
    val mods: Long,
    val items: Long,
    val recipes: Long,
    val bytes: Long,
)
