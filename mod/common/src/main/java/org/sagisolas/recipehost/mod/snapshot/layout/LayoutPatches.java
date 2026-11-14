/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.layout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.sagisolas.recipehost.mod.snapshot.Hashes;

/**
 * Where a drawn image differs from its layout's, in tiles of {@link #TILE} px (ADR 0019). Recipes
 * of one type mostly draw the same layout, apart from an animation frame, a progress bar or their
 * own text, so each sends only the tiles that differ.
 */
final class LayoutPatches {

    /** A patch tile's side in px; the protocol fixes it. */
    static final int TILE = 8;

    private LayoutPatches() {}

    /** The tiles where {@code drawn} differs from {@code base}, both {@code width * height} ARGB. */
    record Diff(List<Integer> tiles, int[] strip) {
        boolean isEmpty() {
            return tiles.isEmpty();
        }

        /** The patch's key: a hash of its tile indices and pixels, truncated to 128 bits and hex-encoded. */
        String key() {
            ByteBuffer buffer = ByteBuffer.allocate(4 * (1 + tiles.size() + strip.length));
            buffer.putInt(tiles.size());
            tiles.forEach(buffer::putInt);
            for (int pixel : strip) buffer.putInt(pixel);
            return Hashes.truncatedSha256Hex(buffer.array());
        }
    }

    /**
     * The tiles of {@code drawn} that differ from {@code base} in any pixel, row-major over
     * {@code ceil(width / TILE)} columns, and their pixels as one strip {@code TILE} px tall, the
     * tiles left to right; a strip pixel past the layout's edge is transparent.
     */
    static Diff diff(int[] base, int[] drawn, int width, int height) {
        int columns = (width + TILE - 1) / TILE;
        int rows = (height + TILE - 1) / TILE;
        List<Integer> tiles = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (differs(base, drawn, width, height, column * TILE, row * TILE)) tiles.add(row * columns + column);
            }
        }
        int stripWidth = tiles.size() * TILE;
        int[] strip = new int[stripWidth * TILE];
        for (int i = 0; i < tiles.size(); i++) {
            int left = tiles.get(i) % columns * TILE;
            int top = tiles.get(i) / columns * TILE;
            for (int y = 0; y < TILE && top + y < height; y++) {
                for (int x = 0; x < TILE && left + x < width; x++) {
                    strip[y * stripWidth + i * TILE + x] = drawn[(top + y) * width + left + x];
                }
            }
        }
        return new Diff(List.copyOf(tiles), strip);
    }

    private static boolean differs(int[] base, int[] drawn, int width, int height, int left, int top) {
        for (int y = top; y < Math.min(top + TILE, height); y++) {
            for (int x = left; x < Math.min(left + TILE, width); x++) {
                if (base[y * width + x] != drawn[y * width + x]) return true;
            }
        }
        return false;
    }
}
