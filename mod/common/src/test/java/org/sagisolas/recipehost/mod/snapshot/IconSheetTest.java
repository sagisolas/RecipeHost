package org.sagisolas.recipehost.mod.snapshot;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;

public class IconSheetTest {

    private static final int CELL = 2;
    private static final int COLUMNS = 3;
    private static final int ROWS = 2;
    private static final int WIDTH = COLUMNS * CELL;

    /** Sheet whose every pixel encodes its own (x, y), fully opaque. */
    private static int[] coordinatePixels() {
        int[] argb = new int[WIDTH * ROWS * CELL];
        for (int y = 0; y < ROWS * CELL; y++)
            for (int x = 0; x < WIDTH; x++)
                argb[y * WIDTH + x] = 0xFF000000 | (x << 8) | y;
        return argb;
    }

    @Test
    public void iconIsTheCellAtItsIndexInRowMajorOrder() {
        IconSheet sheet = new IconSheet(COLUMNS, CELL, 5, coordinatePixels());

        BufferedImage icon = sheet.icon(4); // column 1, row 1

        assertEquals(CELL, icon.getWidth());
        assertEquals(CELL, icon.getHeight());
        assertEquals(0xFF000000 | (2 << 8) | 2, icon.getRGB(0, 0));
        assertEquals(0xFF000000 | (3 << 8) | 3, icon.getRGB(1, 1));
    }

    @Test
    public void transparencyIsKept() {
        int[] argb = coordinatePixels();
        argb[0] = 0x00000000;
        argb[1] = 0x80FF0000;
        IconSheet sheet = new IconSheet(COLUMNS, CELL, 1, argb);

        BufferedImage icon = sheet.icon(0);

        assertEquals(0, icon.getRGB(0, 0) >>> 24);
        assertEquals(0x80FF0000, icon.getRGB(1, 0));
    }

    @Test
    public void countIsTheNumberOfItemsDrawnNotTheGridCapacity() {
        assertEquals(4, new IconSheet(COLUMNS, CELL, 4, coordinatePixels()).count());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void indexPastTheDrawnItemsIsRejected() {
        new IconSheet(COLUMNS, CELL, 4, coordinatePixels()).icon(4);
    }
}
