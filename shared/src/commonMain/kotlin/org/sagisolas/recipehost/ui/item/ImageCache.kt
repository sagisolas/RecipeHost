package org.sagisolas.recipehost.ui.item

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Decoded `Icon atlas`es by atlas id. An atlas never changes and its id is never reused. */
internal typealias IconAtlasCache = ImageCache<Long>

/**
 * Decoded `Recipe layout` images by layout key. A vanilla key names its parameters, not its
 * pixels, so a later Scan can replace its image: `AppGraph` [clears][ImageCache.clear] it when
 * each Scan ends.
 */
internal typealias LayoutImageCache = ImageCache<String>

/**
 * Decoded images by key. Each image is loaded and decoded once, in [scope] (off the main thread),
 * however many callers ask for it at the same time; a caller that stops waiting doesn't cancel it
 * for the others. An image that is gone or fails to decode is remembered as missing, until [clear].
 *
 * Holds at most [capacity] images, dropping the least recently used one; a dropped image is
 * loaded again when next asked for. `AppGraph` keeps 64 Icon atlases of 16×8 32 px cells: about
 * 32 MB, enough for 8192 Icons.
 *
 * Main-thread only: [get] and [peek] are called from composition, [clear] from the main thread.
 */
internal class ImageCache<K : Any>(
    private val scope: CoroutineScope,
    private val load: suspend (key: K) -> ByteArray?,
    private val decode: (ByteArray) -> ImageBitmap = { it.decodeToImageBitmap() },
    private val capacity: Int = 256,
) {
    private val pending = mutableMapOf<K, Deferred<ImageBitmap?>>()

    /** In least-recently-used first order: a hit moves its entry to the end. */
    private val done = LinkedHashMap<K, ImageBitmap?>()

    /**
     * Bumped by each [clear]. Snapshot state, so a composable that read it while drawing an image
     * asks for that image again after a clear.
     */
    var generation by mutableIntStateOf(0)
        private set

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    /** The image if it is already decoded, without waiting; `null` while loading or when missing. */
    fun peek(key: K): ImageBitmap? = if (key in done) touch(key) else null

    /** The decoded image, or `null` when it is missing. */
    suspend fun get(key: K): ImageBitmap? {
        if (key in done) return touch(key)
        val inFlight = pending.getOrPut(key) { scope.async { loadAndDecode(key) } }
        val bitmap = inFlight.await()
        // A clear while this was loading dropped it: the image may be stale, so it isn't kept.
        if (pending[key] === inFlight) {
            pending.remove(key)
            done[key] = bitmap
            if (done.size > capacity) done.remove(done.keys.first())
        }
        return bitmap
    }

    /** Forgets every image, so each is loaded and decoded again when next asked for. */
    fun clear() {
        pending.clear()
        done.clear()
        generation++
    }

    private fun touch(key: K): ImageBitmap? {
        val bitmap = done.remove(key)
        done[key] = bitmap
        return bitmap
    }

    private suspend fun loadAndDecode(key: K): ImageBitmap? =
        try {
            load(key)?.let(decode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
