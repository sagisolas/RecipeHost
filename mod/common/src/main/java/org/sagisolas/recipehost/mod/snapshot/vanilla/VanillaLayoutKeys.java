package org.sagisolas.recipehost.mod.snapshot.vanilla;

/**
 * The parameter keys of the vanilla `Recipe layout`s: they name what varies between the
 * layouts {@link VanillaLayouts} draws, so a key that was already drawn isn't drawn again.
 */
final class VanillaLayoutKeys {

    /** The one vanilla crafting layout: every shaped and shapeless recipe shows the crafting table's grid. */
    static final String CRAFTING = "minecraft:crafting";
    /** The one vanilla smithing transform layout: nothing about it varies by recipe. */
    static final String SMITHING = "minecraft:smithing_transform";
    /** The one vanilla stonecutter layout. */
    static final String STONECUTTER = "minecraft:stonecutting";

    private VanillaLayoutKeys() {}

    /**
     * A vanilla cooking layout (smelting, blasting, smoking, campfire). The cook
     * time and XP are baked into the image as text, so they are part of the key.
     */
    static String cooking(String typeId, int cookTimeTicks, float xp) {
        return typeId + "/" + cookTimeTicks + "/" + xp;
    }
}
