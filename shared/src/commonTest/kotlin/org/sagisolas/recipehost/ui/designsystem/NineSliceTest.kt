package org.sagisolas.recipehost.ui.designsystem

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class NineSliceTest {
    @Test
    fun cornersKeepTheirProportionsAndTheCentreStretches() {
        val slices =
            nineSlice(
                sourceSize = IntSize(20, 20),
                border = SliceInsets(3, 3, 3, 3),
                targetSize = IntSize(100, 40),
                borderScale = 2f,
            )

        val topLeft = Slice(src = IntRect(0, 0, 3, 3), dst = IntRect(0, 0, 6, 6))
        val centre = Slice(src = IntRect(3, 3, 17, 17), dst = IntRect(6, 6, 94, 34))
        val bottomRight = Slice(src = IntRect(17, 17, 20, 20), dst = IntRect(94, 34, 100, 40))
        assertEquals(9, slices.size)
        assertEquals(topLeft, slices[0])
        assertEquals(centre, slices[4])
        assertEquals(bottomRight, slices[8])
    }

    @Test
    fun bordersShrinkProportionallyWhenTheTargetIsTooSmall() {
        val slices =
            nineSlice(
                sourceSize = IntSize(20, 20),
                border = SliceInsets(left = 2, top = 3, right = 6, bottom = 3),
                targetSize = IntSize(8, 40),
                borderScale = 2f,
            )

        val columns = slices.map { it.dst.left to it.dst.right }.distinct()
        assertEquals(listOf(0 to 2, 2 to 8), columns)
    }

    @Test
    fun fractionalScaleStillCoversTheTargetWithoutGaps() {
        val slices =
            nineSlice(
                sourceSize = IntSize(20, 20),
                border = SliceInsets(3),
                targetSize = IntSize(37, 13),
                borderScale = 2.5f,
            )

        val columns = slices.map { it.dst.left to it.dst.right }.distinct()
        val rows = slices.map { it.dst.top to it.dst.bottom }.distinct()
        assertEquals(listOf(0 to 8, 8 to 29, 29 to 37), columns)
        // 13px can't fit two 8px borders: they shrink to 7 + 6 and the middle row disappears.
        assertEquals(listOf(0 to 7, 7 to 13), rows)
    }

    @Test
    fun noBorderDrawsTheWholeTextureAsOneSlice() {
        val slices =
            nineSlice(
                sourceSize = IntSize(20, 10),
                border = SliceInsets(0),
                targetSize = IntSize(50, 30),
                borderScale = 2f,
            )

        assertEquals(listOf(Slice(src = IntRect(0, 0, 20, 10), dst = IntRect(0, 0, 50, 30))), slices)
    }
}
