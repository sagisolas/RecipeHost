package org.sagisolas.recipehost.ui.item

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Decoded `Icon atlas`es by atlas id. Each atlas is loaded and decoded once, in [scope] (off the
 * main thread), however many Icons ask for it at the same time; a caller that stops waiting
 * doesn't cancel it for the others. An atlas never changes and its id is never reused, so a
 * result stays valid for good: an atlas that is gone or fails to decode is remembered as missing.
 *
 * Holds at most [capacity] atlases, dropping the least recently used one (an atlas a re-Scan
 * deleted, or one scrolled far away); a dropped atlas is loaded again when next asked for. The
 * default of 256 atlases of 8×4 16 px cells is about 8 MB, enough for 8192 Icons.
 *
 * [get] and [peek] are main-thread only: they are called from composition.
 */
internal class IconAtlasCache(
    private val scope: CoroutineScope,
    private val load: suspend (atlasId: Long) -> ByteArray?,
    private val decode: (ByteArray) -> ImageBitmap = { it.decodeToImageBitmap() },
    private val capacity: Int = 256,
) {
    private val pending = mutableMapOf<Long, Deferred<ImageBitmap?>>()

    /** In least-recently-used first order: a hit moves its entry to the end. */
    private val done = LinkedHashMap<Long, ImageBitmap?>()

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    /** The atlas if it is already decoded, without waiting; `null` while loading or when missing. */
    fun peek(atlasId: Long): ImageBitmap? = if (atlasId in done) touch(atlasId) else null

    /** The decoded atlas, or `null` when it is missing. */
    suspend fun get(atlasId: Long): ImageBitmap? {
        if (atlasId in done) return touch(atlasId)
        val bitmap = pending.getOrPut(atlasId) { scope.async { loadAndDecode(atlasId) } }.await()
        pending.remove(atlasId)
        done[atlasId] = bitmap
        if (done.size > capacity) done.remove(done.keys.first())
        return bitmap
    }

    private fun touch(atlasId: Long): ImageBitmap? {
        val bitmap = done.remove(atlasId)
        done[atlasId] = bitmap
        return bitmap
    }

    private suspend fun loadAndDecode(atlasId: Long): ImageBitmap? = try {
        load(atlasId)?.let(decode)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
