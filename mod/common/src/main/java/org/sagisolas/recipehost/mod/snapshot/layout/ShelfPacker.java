/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.layout;

import java.util.Optional;

/**
 * Places rectangles on one fixed-size sheet in shelves: left to right along the
 * current shelf, and a new shelf below it when the next one doesn't fit the
 * width left. A shelf is as tall as its tallest rectangle. One packer per sheet;
 * when {@link #place} refuses a rectangle, the sheet is done and the caller
 * starts a new one.
 */
final class ShelfPacker {

    record Position(int x, int y) {}

    private final int width;
    private final int height;
    private int shelfY;
    private int shelfHeight;
    private int cursorX;

    ShelfPacker(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Where a {@code w}×{@code h} rectangle goes, or empty if it no longer fits on this sheet. */
    Optional<Position> place(int w, int h) {
        if (cursorX + w > width) {
            shelfY += shelfHeight;
            shelfHeight = 0;
            cursorX = 0;
        }
        if (w > width || shelfY + h > height) return Optional.empty();
        Position position = new Position(cursorX, shelfY);
        cursorX += w;
        shelfHeight = Math.max(shelfHeight, h);
        return Optional.of(position);
    }
}
