package org.sagisolas.recipehost.mod.snapshot;

import org.sagisolas.recipehost.protocol.RecipeSlot;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Keys that name a `Recipe layout` on the wire, so equal layouts are sent and stored once. */
final class LayoutKeys {

    private static final int KEY_BYTES = 16;

    /** The one vanilla crafting layout: every shaped and shapeless recipe shows the crafting table's grid. */
    static final String CRAFTING = "minecraft:crafting";
    /** The one vanilla smithing transform layout: nothing about it varies by recipe. */
    static final String SMITHING = "minecraft:smithing_transform";
    /** The one vanilla stonecutter layout. */
    static final String STONECUTTER = "minecraft:stonecutting";

    private LayoutKeys() {}

    /**
     * A vanilla cooking layout (smelting, blasting, smoking, campfire). The cook
     * time and XP are baked into the image as text, so they are part of the key.
     */
    static String cooking(String typeId, int cookTimeTicks, float xp) {
        return typeId + "/" + cookTimeTicks + "/" + xp;
    }

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
            buffer.putInt(slot.x).putInt(slot.y).putInt(slot.width).putInt(slot.height).putInt(slot.role.getValue());
        }
        byte[] digest = sha256().digest(buffer.array());
        return HexFormat.of().formatHex(digest, 0, KEY_BYTES);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM ships SHA-256", e);
        }
    }
}
