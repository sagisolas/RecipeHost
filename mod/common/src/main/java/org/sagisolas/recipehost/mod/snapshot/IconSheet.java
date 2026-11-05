package org.sagisolas.recipehost.mod.snapshot;

import java.awt.image.BufferedImage;

/**
 * One read-back of {@link IconRenderer}'s grid: square icons laid out row-major,
 * {@code columns} per row, as top-down ARGB pixels. Plain CPU data, so cropping
 * (and {@link PngEncoder} encoding) run off the render thread.
 */
final class IconSheet {

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

    int count() {
        return count;
    }

    /** The icon drawn at {@code index}. */
    BufferedImage icon(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        int width = columns * cell;
        int x0 = (index % columns) * cell;
        int y0 = (index / columns) * cell;
        BufferedImage icon = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
        icon.setRGB(0, 0, cell, cell, argb, y0 * width + x0, width);
        return icon;
    }
}
