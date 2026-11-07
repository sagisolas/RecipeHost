package org.sagisolas.recipehost.mod.snapshot.layout;

import org.junit.Test;
import org.sagisolas.recipehost.protocol.RecipeSlot;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LayoutKeysTest {

    private static final int W = 4;
    private static final int H = 3;

    private static int[] pixels() {
        int[] argb = new int[W * H];
        for (int i = 0; i < argb.length; i++) argb[i] = 0xFF000000 | i * 0x010203;
        return argb;
    }

    private static RecipeSlot slot(int x, int y, RecipeSlot.Role role) {
        return new RecipeSlot.Builder().x(x).y(y).width(18).height(18).role(role).build();
    }

    private static final List<RecipeSlot> SLOTS = List.of(
            slot(0, 0, RecipeSlot.Role.INPUT), slot(40, 0, RecipeSlot.Role.OUTPUT));

    @Test
    public void equalPixelsAndRectanglesGiveTheSameKey() {
        assertEquals(
                LayoutKeys.contentHash(pixels(), W, H, SLOTS),
                LayoutKeys.contentHash(pixels(), W, H, List.copyOf(SLOTS)));
    }

    @Test
    public void movingOneRectangleChangesTheKey() {
        List<RecipeSlot> moved = List.of(slot(0, 0, RecipeSlot.Role.INPUT), slot(41, 0, RecipeSlot.Role.OUTPUT));

        assertNotEquals(LayoutKeys.contentHash(pixels(), W, H, SLOTS), LayoutKeys.contentHash(pixels(), W, H, moved));
    }

    @Test
    public void changingOnePixelChangesTheKey() {
        int[] changed = pixels();
        changed[5] ^= 1;

        assertNotEquals(LayoutKeys.contentHash(pixels(), W, H, SLOTS), LayoutKeys.contentHash(changed, W, H, SLOTS));
    }

    @Test
    public void theSameBytesInADifferentShapeGiveADifferentKey() {
        assertNotEquals(LayoutKeys.contentHash(pixels(), W, H, SLOTS), LayoutKeys.contentHash(pixels(), H, W, SLOTS));
    }

    @Test
    public void theKeyIs128BitsOfLowercaseHex() {
        String key = LayoutKeys.contentHash(pixels(), W, H, SLOTS);

        assertEquals(32, key.length());
        assertTrue(key, key.matches("[0-9a-f]{32}"));
    }

    @Test
    public void theKeyMatchesAKnownSha256Prefix() {
        // A 1x1 transparent-black layout with no slots hashes the 16 bytes
        // width=1, height=1 (big-endian ints), RGBA 00000000, slot count 0.
        // sha256(00000001 00000001 00000000 00000000) truncated to 128 bits.
        assertEquals("d66c3b2546298fcb2c1a95a86cf0c952", LayoutKeys.contentHash(new int[]{0}, 1, 1, List.of()));
    }
}
