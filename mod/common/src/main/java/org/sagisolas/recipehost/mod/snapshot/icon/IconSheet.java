package org.sagisolas.recipehost.mod.snapshot.icon;

import java.awt.image.BufferedImage;

/**
 * One read-back of {@link IconRenderer}'s grid: square icons laid out row-major,
 * {@code columns} per row, as top-down ARGB pixels. Plain CPU data, so building
 * the atlas image (and {@link org.sagisolas.recipehost.mod.snapshot.image.PngEncoder} encoding it) runs off the render thread.
 */
public final class IconSheet {

    private final int columns;
    private final int cell;
    private final int count;
    private final int[] argb;

    /**
     * @param count number of cells actually drawn, starting at index 0
     * @param argb  top-down, row-major pixels of the whole sheet; width is {@code columns * cell}
     */
    IconSheet(int columns, int cell, int count, int[] argb) {
        this.columns = columns;
        this.cell = cell;
        this.count = count;
        this.argb = argb;
    }

    public int count() {
        return count;
    }

    public int columns() {
        return columns;
    }

    public int cell() {
        return cell;
    }

    /**
     * The sheet as an Icon atlas: full width, cut after the last row that holds a
     * drawn cell, so a partly filled sheet doesn't carry empty rows.
     */
    public BufferedImage image() {
        int width = columns * cell;
        int height = Math.max(1, (count + columns - 1) / columns) * cell;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }
}
