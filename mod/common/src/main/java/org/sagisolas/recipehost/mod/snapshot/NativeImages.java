package org.sagisolas.recipehost.mod.snapshot;

import com.mojang.blaze3d.platform.NativeImage;

/** Reading rendered sheets back into plain pixels. */
final class NativeImages {

    private NativeImages() {}

    /** The image's pixels, top-down and row-major. NativeImage stores ABGR; swap red and blue to get ARGB. */
    static int[] toArgb(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = image.getPixelRGBA(x, y);
                argb[y * width + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >>> 16) & 0xFF);
            }
        }
        return argb;
    }
}
