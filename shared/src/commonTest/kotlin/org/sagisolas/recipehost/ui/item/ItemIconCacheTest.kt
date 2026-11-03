package org.sagisolas.recipehost.ui.item

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ItemIconCacheTest {
    private val pngA = byteArrayOf(1, 2, 3)

    @Test
    fun missingPngGivesNoIcon() {
        val cache = ItemIconCache { error("must not decode") }

        assertNull(cache.get("minecraft:stone", null))
    }

    @Test
    fun undecodablePngGivesNoIcon() {
        val cache = ItemIconCache { throw IllegalArgumentException("not a png") }

        assertNull(cache.get("minecraft:stone", pngA))
    }

    @Test
    fun eachKeyIsDecodedOnce() {
        var decodes = 0
        val cache = ItemIconCache { decodes++; FakeImageBitmap() }

        val first = cache.get("minecraft:stone", pngA)
        val second = cache.get("minecraft:stone", pngA)
        cache.get("minecraft:dirt", pngA)

        assertSame(first, second)
        assertEquals(2, decodes)
    }

    @Test
    fun iconArrivingLaterIsDecoded() {
        val bitmap = FakeImageBitmap()
        val cache = ItemIconCache { bitmap }

        cache.get("minecraft:stone", null)

        assertSame(bitmap, cache.get("minecraft:stone", pngA))
    }

    @Test
    fun samePngInANewArrayIsNotDecodedAgain() {
        var decodes = 0
        val cache = ItemIconCache { decodes++; FakeImageBitmap() }

        cache.get("minecraft:stone", pngA)
        cache.get("minecraft:stone", pngA.copyOf())

        assertEquals(1, decodes)
    }

    @Test
    fun changedPngForAKeyIsDecodedAgain() {
        val fixed = FakeImageBitmap()
        val cache = ItemIconCache { png -> if (png.contentEquals(pngA)) error("broken") else fixed }

        cache.get("minecraft:stone", pngA)

        assertSame(fixed, cache.get("minecraft:stone", byteArrayOf(9, 9)))
    }
}

private class FakeImageBitmap : ImageBitmap {
    override val width = 16
    override val height = 16
    override val config = ImageBitmapConfig.Argb8888
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha = true
    override fun prepareToDraw() = Unit
    override fun readPixels(
        buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int,
    ) = Unit
}
