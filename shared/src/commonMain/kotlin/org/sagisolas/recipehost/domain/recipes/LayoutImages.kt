/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.domain.recipes

import kotlinx.coroutines.withContext
import org.sagisolas.recipehost.data.catalog.CatalogDatabase
import kotlin.coroutines.CoroutineContext

/**
 * Reads `Recipe layout` and layout patch PNGs out of the `Catalog` by key. A later Scan can replace the
 * image under a key (the latest Scan wins), so a caller that keeps what it read must drop it then.
 *
 * [context] runs the queries; `AppGraph` passes an I/O dispatcher.
 */
internal class LayoutImages(
    private val catalog: CatalogDatabase,
    private val context: CoroutineContext,
) {
    /** The image's PNG, or `null` if no recipe uses it any more (or the Catalog was cleared). */
    suspend fun png(image: LayoutImageKey): ByteArray? =
        withContext(context) {
            when (image) {
                is LayoutImageKey.Layout -> catalog.recipeLayoutQueries.pngOf(image.key)
                is LayoutImageKey.Patch -> catalog.recipeLayoutQueries.patchPngOf(image.key)
            }.executeAsOneOrNull()
        }
}

/** A `Recipe layout`'s image or a layout patch's (ADR 0019), by its key. */
internal sealed interface LayoutImageKey {
    val key: String

    data class Layout(
        override val key: String,
    ) : LayoutImageKey

    data class Patch(
        override val key: String,
    ) : LayoutImageKey
}
