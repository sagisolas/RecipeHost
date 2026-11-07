package org.sagisolas.recipehost.domain.recipes

import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import kotlin.coroutines.CoroutineContext

/**
 * Reads `Recipe layout` PNGs out of the `Catalog` by layout key. A later Scan can replace the
 * image under a key (the latest Scan wins), so a caller that keeps what it read must drop it then.
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class LayoutImages(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    /** The layout's PNG, or `null` if no recipe uses the layout any more (or the Catalog was cleared). */
    suspend fun png(key: String): ByteArray? = withContext(context) {
        catalog.recipeLayoutQueries.pngOf(key).executeAsOneOrNull()
    }
}
