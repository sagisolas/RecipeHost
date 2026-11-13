/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.icon;

import java.awt.image.BufferedImage;
import org.sagisolas.recipehost.mod.snapshot.image.NativeImages;

/**
 * One read-back of {@link IconRenderer}'s grid: square icons laid out row-major,
 * {@code columns} per row, as top-down ARGB pixels. Plain CPU data, so building
 * the atlas image (and {@link org.sagisolas.recipehost.mod.snapshot.image.PngEncoder} encoding it) runs off the render thread.
 */
public final class IconSheet {

    private final int columns;
    private final int cell;
    private final int count;
    /** Top-down ARGB, or null until {@link #readback} is converted. */
    private int[] argb;

    private final int[] readback;

    /**
     * @param count number of cells actually drawn, starting at index 0
     * @param argb  top-down, row-major pixels of the whole sheet; width is {@code columns * cell}
     */
    public IconSheet(int columns, int cell, int count, int[] argb) {
        this(columns, cell, count, argb, null);
    }

    private IconSheet(int columns, int cell, int count, int[] argb, int[] readback) {
        this.columns = columns;
        this.cell = cell;
        this.count = count;
        this.argb = argb;
        this.readback = readback;
    }

    /**
     * A sheet straight from a GL read-back ({@link NativeImages#readback}): it is turned into
     * top-down ARGB when the atlas is first built, off the render thread.
     */
    static IconSheet fromReadback(int columns, int cell, int rows, int count, int[] abgrBottomUp) {
        if (abgrBottomUp.length != columns * cell * rows * cell) {
            throw new IllegalArgumentException(
                    "a " + columns + "x" + rows + " sheet read back " + abgrBottomUp.length + " pixels");
        }
        return new IconSheet(columns, cell, count, null, abgrBottomUp);
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
        image.setRGB(0, 0, width, height, argb(), 0, width);
        return image;
    }

    private int[] argb() {
        if (argb == null) argb = NativeImages.argbTopDown(readback, columns * cell, readback.length / (columns * cell));
        return argb;
    }
}
