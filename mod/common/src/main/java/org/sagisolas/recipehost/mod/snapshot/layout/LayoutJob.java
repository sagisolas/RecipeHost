package org.sagisolas.recipehost.mod.snapshot.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.sagisolas.recipehost.protocol.RecipeSlot;

import java.util.List;
import java.util.Objects;

/**
 * A `Recipe layout` waiting to be rendered: what to draw, how big it is, where its
 * `Recipe slot`s are, how it is keyed and which recipes use it. Layout sources
 * (vanilla, EMI) make these while mapping recipes; {@link LayoutBatches} renders
 * them after the recipes have been sent.
 *
 * @param typeId       recipe type (or EMI category) id, for logging a failed draw once per type
 * @param width        GUI px
 * @param height       GUI px
 * @param parameterKey the key when it is known before drawing, or null to key by
 *                     {@link LayoutKeys#contentHash} once drawn
 */
public record LayoutJob(
        String typeId,
        int width,
        int height,
        List<RecipeSlot> slots,
        String parameterKey,
        List<String> recipeIds,
        Drawer drawer) {

    /** Draws the layout with its top-left corner at (0, 0). Runs on the render thread. */
    @FunctionalInterface
    public interface Drawer {
        void draw(GuiGraphics graphics, int mouseX, int mouseY);
    }

    public LayoutJob {
        Objects.requireNonNull(typeId);
        Objects.requireNonNull(drawer);
        slots = List.copyOf(slots);
        recipeIds = List.copyOf(recipeIds);
    }

    /** This job, used by {@code recipeIds} instead. */
    LayoutJob withRecipeIds(List<String> recipeIds) {
        return new LayoutJob(typeId, width, height, slots, parameterKey, recipeIds, drawer);
    }

    /** A layout whose key is known up front, such as {@code minecraft:crafting_shaped/3x3}. */
    public static LayoutJob keyed(String key, String typeId, int width, int height,
                           List<RecipeSlot> slots, List<String> recipeIds, Drawer drawer) {
        return new LayoutJob(typeId, width, height, slots, Objects.requireNonNull(key), recipeIds, drawer);
    }

    /** A layout keyed by a hash of what it draws, such as an EMI recipe's. */
    public static LayoutJob hashed(String typeId, int width, int height,
                            List<RecipeSlot> slots, List<String> recipeIds, Drawer drawer) {
        return new LayoutJob(typeId, width, height, slots, null, recipeIds, drawer);
    }
}
