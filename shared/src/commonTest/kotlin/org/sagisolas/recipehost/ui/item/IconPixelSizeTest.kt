package org.sagisolas.recipehost.ui.item

import kotlin.test.Test
import kotlin.test.assertEquals

class IconPixelSizeTest {
    @Test
    fun keepsSizesThatAreAlreadyAWholeNumberOfPixelsPerTexel() {
        assertEquals(64, iconPixelSize(64f))
        assertEquals(96, iconPixelSize(96f))
        assertEquals(128, iconPixelSize(128f))
    }

    @Test
    fun roundsFractionalPixelsPerTexelDownSoTheIconStaysInsideItsSlot() {
        assertEquals(80, iconPixelSize(88f)) // 2.75× density
        assertEquals(80, iconPixelSize(84f)) // 2.625× density
        assertEquals(112, iconPixelSize(112.5f)) // 3.5× density, float noise
    }

    @Test
    fun neverShrinksBelowOnePixelPerTexel() {
        assertEquals(16, iconPixelSize(24f))
    }

    @Test
    fun usesTheWholeAreaWhenItIsSmallerThanOneTexelPerPixel() {
        assertEquals(12, iconPixelSize(12f))
    }
}
