package org.sagisolas.recipehost.mod.snapshot;

import org.sagisolas.recipehost.protocol.RecipeSlot;

import java.util.List;

/**
 * One layout {@link VanillaLayouts} draws: its parameter key, its size in GUI px,
 * its `Recipe slot`s in the order {@link RecipeMapper#layoutStacks} fills them,
 * and how to draw it.
 */
record VanillaLayout(String key, int width, int height, List<RecipeSlot> slots, LayoutJob.Drawer drawer) {

    VanillaLayout {
        slots = List.copyOf(slots);
    }

    /** The job that renders this layout for the recipe {@code recipeId} of type {@code typeId}. */
    LayoutJob job(String typeId, String recipeId) {
        return LayoutJob.keyed(key, typeId, width, height, slots, List.of(recipeId), drawer);
    }
}
