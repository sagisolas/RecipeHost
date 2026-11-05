package org.sagisolas.recipehost.ui.item

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class IconAtlasCacheTest {
    private val png = byteArrayOf(1, 2, 3)

    private fun TestScope.cache(load: suspend (Long) -> ByteArray?, decode: (ByteArray) -> ImageBitmap) =
        IconAtlasCache(backgroundScope, load, decode)

    @Test
    fun eachAtlasIsLoadedAndDecodedOnce() = runTest {
        var loads = 0
        var decodes = 0
        val cache = cache(load = { loads++; png }, decode = { decodes++; FakeImageBitmap() })

        val first = cache.get(1)
        val second = cache.get(1)
        cache.get(2)

        assertSame(first, second)
        assertEquals(2, loads)
        assertEquals(2, decodes)
    }

    @Test
    fun iconsWaitingForTheSameAtlasShareOneLoad() = runTest {
        var loads = 0
        val gate = CompletableDeferred<Unit>()
        val cache = cache(load = { loads++; gate.await(); png }, decode = { FakeImageBitmap() })

        val a = async { cache.get(1) }
        val b = async { cache.get(1) }
        testScheduler.runCurrent()
        gate.complete(Unit)

        assertSame(a.await(), b.await())
        assertEquals(1, loads)
    }

    @Test
    fun peekGivesTheAtlasOnlyOnceItIsDecoded() = runTest {
        val bitmap = FakeImageBitmap()
        val cache = cache(load = { png }, decode = { bitmap })

        assertNull(cache.peek(1))
        cache.get(1)

        assertSame(bitmap, cache.peek(1))
    }

    @Test
    fun aMissingAtlasGivesNoBitmapAndIsNotLoadedAgain() = runTest {
        var loads = 0
        val cache = cache(load = { loads++; null }, decode = { error("must not decode") })

        assertNull(cache.get(1))
        assertNull(cache.get(1))
        assertEquals(1, loads)
    }

    @Test
    fun theLeastRecentlyUsedAtlasIsDroppedPastCapacity() = runTest {
        val loads = mutableListOf<Long>()
        val cache = IconAtlasCache(backgroundScope, load = { loads += it; png }, decode = { FakeImageBitmap() }, capacity = 2)

        cache.get(1)
        cache.get(2)
        cache.peek(1) // 1 is now more recent than 2
        cache.get(3) // drops 2

        assertNull(cache.peek(2))
        cache.get(1)
        cache.get(2)
        assertEquals(listOf(1L, 2L, 3L, 2L), loads)
    }

    @Test
    fun anUndecodableAtlasGivesNoBitmap() = runTest {
        val cache = cache(load = { png }, decode = { throw IllegalArgumentException("not a png") })

        assertNull(cache.get(1))
    }

    @Test
    fun aFailingLoadGivesNoBitmap() = runTest {
        val cache = cache(load = { throw IllegalStateException("database closed") }, decode = { FakeImageBitmap() })

        assertNull(cache.get(1))
    }
}

private class FakeImageBitmap : ImageBitmap {
    override val width = 128
    override val height = 64
    override val config = ImageBitmapConfig.Argb8888
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha = true
    override fun prepareToDraw() = Unit
    override fun readPixels(
        buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int,
    ) = Unit
}
