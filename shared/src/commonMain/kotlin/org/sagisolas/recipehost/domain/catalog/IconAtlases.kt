package org.sagisolas.recipehost.domain.catalog

import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import kotlin.coroutines.CoroutineContext

/**
 * Reads `Icon atlas` PNGs out of the `Catalog`, which [IconRegion]s point into. An atlas never
 * changes once written, so a caller may keep what it read for as long as it likes.
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class IconAtlases(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    /** The atlas PNG, or `null` if it is gone (its cells were all replaced, or the Catalog cleared). */
    suspend fun png(atlasId: Long): ByteArray? =
        withContext(context) {
            catalog.iconQueries.atlasPng(atlasId).executeAsOneOrNull()
        }
}
