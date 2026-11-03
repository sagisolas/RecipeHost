package org.sagisolas.recipehost.ui.item

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Decoded Icons by item key. A key is decoded again only when its png bytes change, so a fresh
 * copy of the same bytes (as each catalog query returns) is a hit. A `null` png (Icon not
 * arrived yet) is not remembered; a png that fails to decode is remembered as missing.
 *
 * Unbounded and main-thread only: it is read from composition.
 */
internal class ItemIconCache(
    private val decode: (ByteArray) -> ImageBitmap = { it.decodeToImageBitmap() },
) {
    private class Entry(val png: ByteArray, val bitmap: ImageBitmap?)

    private val entries = mutableMapOf<String, Entry>()

    fun get(key: String, png: ByteArray?): ImageBitmap? {
        if (png == null) return null
        entries[key]?.let { if (it.png.contentEquals(png)) return it.bitmap }
        val bitmap = try {
            decode(png)
        } catch (e: Exception) {
            null
        }
        entries[key] = Entry(png, bitmap)
        return bitmap
    }
}
