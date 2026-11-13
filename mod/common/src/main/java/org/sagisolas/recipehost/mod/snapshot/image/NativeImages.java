/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.image;

import com.mojang.blaze3d.platform.NativeImage;

/** Reading rendered sheets back into plain pixels. */
public final class NativeImages {

    private NativeImages() {}

    /**
     * The image's pixels as GL read them back: rows bottom-up, each pixel ABGR. Only a copy, so the
     * render thread does as little as it can; {@link #argbTopDown} turns them into an image's pixels
     * off it.
     */
    public static int[] readback(NativeImage image) {
        return image.getPixelsRGBA();
    }

    /**
     * Pixels of a GL read-back ({@code width × height}, rows bottom-up, each pixel ABGR) as an
     * image's: rows top-down, each pixel ARGB (red and blue swapped).
     */
    public static int[] argbTopDown(int[] abgrBottomUp, int width, int height) {
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int from = (height - 1 - y) * width;
            int to = y * width;
            for (int x = 0; x < width; x++) {
                int abgr = abgrBottomUp[from + x];
                argb[to + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >>> 16) & 0xFF);
            }
        }
        return argb;
    }
}
