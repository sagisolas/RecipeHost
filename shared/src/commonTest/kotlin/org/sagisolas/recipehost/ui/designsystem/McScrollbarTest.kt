/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McScrollbarTest {
    @Test
    fun contentThatFitsHasNoThumb() {
        assertNull(
            scrollbarThumb(
                ScrollExtents(viewport = 500f, content = 500f, scrolled = 0f),
                trackHeight = 400f,
                minHeight = 32f,
            ),
        )
    }

    @Test
    fun theThumbIsTheVisibleShareOfTheTrackAtTheTop() {
        val thumb =
            scrollbarThumb(
                ScrollExtents(viewport = 500f, content = 2000f, scrolled = 0f),
                trackHeight = 400f,
                minHeight = 32f,
            )

        assertEquals(ThumbSpan(top = 0f, height = 100f), thumb)
    }

    @Test
    fun theThumbMovesWithTheScrolledFraction() {
        val halfway =
            scrollbarThumb(
                ScrollExtents(viewport = 500f, content = 2000f, scrolled = 750f),
                trackHeight = 400f,
                minHeight = 32f,
            )
        val end =
            scrollbarThumb(
                ScrollExtents(viewport = 500f, content = 2000f, scrolled = 1500f),
                trackHeight = 400f,
                minHeight = 32f,
            )

        assertEquals(ThumbSpan(top = 150f, height = 100f), halfway)
        assertEquals(ThumbSpan(top = 300f, height = 100f), end)
    }

    @Test
    fun aLongListKeepsTheMinimumThumbAndStaysOnTheTrack() {
        val end =
            scrollbarThumb(
                ScrollExtents(viewport = 500f, content = 1_000_000f, scrolled = 2_000_000f),
                trackHeight = 400f,
                minHeight = 32f,
            )

        assertEquals(ThumbSpan(top = 368f, height = 32f), end)
    }

    @Test
    fun aTrackShorterThanTheMinimumIsFilled() {
        val thumb =
            scrollbarThumb(
                ScrollExtents(viewport = 10f, content = 1000f, scrolled = 0f),
                trackHeight = 20f,
                minHeight = 32f,
            )

        assertEquals(ThumbSpan(top = 0f, height = 20f), thumb)
    }

    private fun extents(
        firstVisibleItem: Int = 0,
        firstVisibleItemOffset: Int = 0,
        atEnd: Boolean = false,
        totalItems: Int = 50,
    ) = gridScrollExtents(
        totalItems = totalItems,
        columns = 8,
        rowHeight = 36,
        spacing = 4,
        contentPadding = 16,
        viewport = 200,
        firstVisibleItem = firstVisibleItem,
        firstVisibleItemOffset = firstVisibleItemOffset,
        atEnd = atEnd,
    )

    @Test
    fun aPartlyFilledLastRowCountsAsARow() {
        // 50 items in rows of 8 are 7 rows: 7 * 40 - 4 + 16.
        assertEquals(ScrollExtents(viewport = 200f, content = 292f, scrolled = 0f), extents())
    }

    @Test
    fun scrolledIsTheRowsAboveTheFirstVisibleItemPlusItsOffset() {
        // Item 19 is in row 2, which starts 2 * 40 px down.
        assertEquals(90f, extents(firstVisibleItem = 19, firstVisibleItemOffset = 10).scrolled)
    }

    @Test
    fun theEndOfTheGridCountsAsFullyScrolled() {
        assertEquals(92f, extents(firstVisibleItem = 24, firstVisibleItemOffset = 3, atEnd = true).scrolled)
    }

    @Test
    fun aSingleRowFitsWithNothingToScroll() {
        val single = extents(totalItems = 5)

        assertNull(scrollbarThumb(single, trackHeight = 192f, minHeight = 64f))
    }

    @Test
    fun aThumbTopIsTheSameShareOfTheScroll() {
        // A 100 px thumb on a 400 px track travels 300 px.
        assertEquals(
            750f,
            scrolledForThumbTop(thumbTop = 150f, trackHeight = 400f, thumbHeight = 100f, maxScroll = 1500f),
        )
    }

    @Test
    fun aThumbTopPastEitherEndScrollsToThatEnd() {
        assertEquals(
            0f,
            scrolledForThumbTop(thumbTop = -40f, trackHeight = 400f, thumbHeight = 100f, maxScroll = 1500f),
        )
        assertEquals(
            1500f,
            scrolledForThumbTop(thumbTop = 390f, trackHeight = 400f, thumbHeight = 100f, maxScroll = 1500f),
        )
    }

    @Test
    fun aThumbThatFillsTheTrackDoesntScroll() {
        assertEquals(0f, scrolledForThumbTop(thumbTop = 10f, trackHeight = 400f, thumbHeight = 400f, maxScroll = 1500f))
    }

    @Test
    fun theThumbAndTheDragAgree() {
        val extents = ScrollExtents(viewport = 500f, content = 2000f, scrolled = 600f)
        val thumb = scrollbarThumb(extents, trackHeight = 400f, minHeight = 64f)!!

        assertEquals(600f, scrolledForThumbTop(thumb.top, trackHeight = 400f, thumb.height, extents.maxScroll))
    }

    @Test
    fun aScrollIsTheFirstItemOfItsRowAndTheDistanceIntoIt() {
        assertEquals(
            GridPosition(index = 16, offset = 10),
            gridPositionAt(scrolled = 90f, columns = 8, rowExtent = 40f),
        )
        assertEquals(GridPosition(index = 0, offset = 0), gridPositionAt(scrolled = -5f, columns = 8, rowExtent = 40f))
    }
}
