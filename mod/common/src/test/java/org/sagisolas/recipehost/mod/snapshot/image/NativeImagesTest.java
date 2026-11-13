/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.image;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class NativeImagesTest {

    @Test
    public void aGlReadbackBecomesTopDownArgb() {
        // A 2×2 GL read-back: rows bottom-up, each pixel ABGR (red in the low byte).
        int[] abgrBottomUp = {
            0xFF0000FF, 0x80FF0000, // bottom row: opaque red, half-transparent blue
            0xFF00FF00, 0x00000000, // top row: opaque green, transparent
        };

        assertArrayEquals(
                new int[] {
                    0xFF00FF00, 0x00000000,
                    0xFFFF0000, 0x800000FF,
                },
                NativeImages.argbTopDown(abgrBottomUp, 2, 2));
    }
}
