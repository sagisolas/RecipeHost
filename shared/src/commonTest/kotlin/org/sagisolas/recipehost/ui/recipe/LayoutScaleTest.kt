/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.recipe

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutScaleTest {
    @Test
    fun theLargestWholeScaleThatFitsIsChosen() {
        assertEquals(6, layoutScale(availableWidthPx = 1080, layoutWidth = 176))
        assertEquals(6, layoutScale(availableWidthPx = 1056, layoutWidth = 176), "6 × 176 = 1056 fits exactly")
        assertEquals(5, layoutScale(availableWidthPx = 1055, layoutWidth = 176))
        assertEquals(1, layoutScale(availableWidthPx = 176, layoutWidth = 176))
    }

    @Test
    fun aLayoutWiderThanTheSectionIsStillDrawnAtScaleOne() {
        assertEquals(1, layoutScale(availableWidthPx = 100, layoutWidth = 176))
    }

    @Test
    fun slotRectanglesScaleWithTheImage() {
        assertEquals(
            IntRect(left = 2, top = 4, right = 38, bottom = 40),
            scaledRect(x = 1, y = 2, width = 18, height = 18, scale = 2),
        )
        assertEquals(
            IntRect(left = 300, top = 102, right = 408, bottom = 210),
            scaledRect(x = 50, y = 17, width = 18, height = 18, scale = 6),
        )
    }
}
