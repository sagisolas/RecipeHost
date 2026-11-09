package org.sagisolas.recipehost.mod.snapshot.image;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class NativeImagesTest {

    @Test
    public void aGlReadbackBecomesTopDownArgb() {
        // A 2×2 GL read-back: rows bottom-up, each pixel ABGR (red in the low byte).
        int[] abgrBottomUp = {
                0xFF0000FF, 0x80FF0000, // bottom row: opaque red, half-transparent blue
                0xFF00FF00, 0x00000000, // top row: opaque green, transparent
        };

        assertArrayEquals(new int[] {
                0xFF00FF00, 0x00000000,
                0xFFFF0000, 0x800000FF,
        }, NativeImages.argbTopDown(abgrBottomUp, 2, 2));
    }
}
