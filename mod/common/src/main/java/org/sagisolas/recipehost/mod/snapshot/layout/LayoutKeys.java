package org.sagisolas.recipehost.mod.snapshot.layout;

import java.nio.ByteBuffer;
import java.util.List;
import org.sagisolas.recipehost.mod.snapshot.Hashes;
import org.sagisolas.recipehost.protocol.RecipeSlot;

/**
 * The key of a `Recipe layout` known only once it is drawn, so equal layouts are sent and stored
 * once. Layout sources that know their key up front (vanilla) name it themselves.
 */
final class LayoutKeys {

    private LayoutKeys() {}

    /**
     * The key of a layout known only once drawn (EMI): SHA-256 over its size, its
     * cropped pixels as RGBA, and its slot rectangles and roles in order, truncated
     * to 128 bits and hex-encoded. All ints are big-endian.
     *
     * @param argb top-down, row-major pixels, {@code width * height} of them
     */
    static String contentHash(int[] argb, int width, int height, List<RecipeSlot> slots) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * (2 + argb.length + 1 + 5 * slots.size()));
        buffer.putInt(width).putInt(height);
        for (int pixel : argb) buffer.putInt((pixel << 8) | (pixel >>> 24));
        buffer.putInt(slots.size());
        for (RecipeSlot slot : slots) {
            buffer.putInt(slot.getX())
                    .putInt(slot.getY())
                    .putInt(slot.getWidth())
                    .putInt(slot.getHeight())
                    .putInt(slot.getRoleValue());
        }
        return Hashes.truncatedSha256Hex(buffer.array());
    }
}
