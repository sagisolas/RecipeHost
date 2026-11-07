package org.sagisolas.recipehost.mod.snapshot.icon;

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
    public void imageKeepsEveryCellAtItsRowMajorPosition() {
        IconSheet sheet = new IconSheet(COLUMNS, CELL, 5, coordinatePixels());

        BufferedImage image = sheet.image();

        assertEquals(WIDTH, image.getWidth());
        assertEquals(ROWS * CELL, image.getHeight());
        // Cell 4 is column 1, row 1.
        assertEquals(0xFF000000 | (2 << 8) | 2, image.getRGB(2, 2));
        assertEquals(0xFF000000 | (3 << 8) | 3, image.getRGB(3, 3));
    }

    @Test
    public void imageIsCutAfterTheLastRowWithADrawnCell() {
        IconSheet sheet = new IconSheet(COLUMNS, CELL, COLUMNS, coordinatePixels());

        BufferedImage image = sheet.image();

        assertEquals(WIDTH, image.getWidth());
        assertEquals(CELL, image.getHeight());
    }

    @Test
    public void transparencyIsKept() {
        int[] argb = coordinatePixels();
        argb[0] = 0x00000000;
        argb[1] = 0x80FF0000;

        BufferedImage image = new IconSheet(COLUMNS, CELL, 1, argb).image();

        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0x80FF0000, image.getRGB(1, 0));
    }

    @Test
    public void countIsTheNumberOfItemsDrawnNotTheGridCapacity() {
        assertEquals(4, new IconSheet(COLUMNS, CELL, 4, coordinatePixels()).count());
    }
}
