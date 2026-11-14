/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.ui.recipe

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

class PatchTilesTest {
    @Test
    fun eachTileIsCopiedFromItsPlaceInTheStripToItsPlaceInTheLayout() {
        assertEquals(
            listOf(
                PatchTile(src = IntRect(0, 0, 8, 8), dst = IntRect(0, 0, 8, 8)),
                PatchTile(src = IntRect(8, 0, 16, 8), dst = IntRect(8, 16, 16, 24)),
            ),
            // 24 x 24: three columns of tiles, so tile 7 is the middle one of the third row.
            patchTiles(tiles = listOf(0, 7), layoutWidth = 24, layoutHeight = 24),
        )
    }

    @Test
    fun aTileAtTheRightOrBottomEdgeIsCutToTheLayout() {
        assertEquals(
            // 20 x 10: tile 5 is the last of the second row, 4 px wide and 2 px tall.
            listOf(PatchTile(src = IntRect(0, 0, 4, 2), dst = IntRect(16, 8, 20, 10))),
            patchTiles(tiles = listOf(5), layoutWidth = 20, layoutHeight = 10),
        )
    }
}
