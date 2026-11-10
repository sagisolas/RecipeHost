package org.sagisolas.recipehost.mod.snapshot.vanilla;

import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.sagisolas.recipehost.mod.snapshot.recipe.TypeNames;

/**
 * Names for the vanilla dispatch's recipe types, from the game's own translations of the block or
 * screen that makes them. Recipe types have no names of their own; a modded type gets none.
 */
final class VanillaTypeNames {

    private static final Map<String, String> KEYS = Map.of(
            "minecraft:crafting", "container.crafting",
            "minecraft:smelting", "block.minecraft.furnace",
            "minecraft:blasting", "block.minecraft.blast_furnace",
            "minecraft:smoking", "block.minecraft.smoker",
            "minecraft:campfire_cooking", "block.minecraft.campfire",
            "minecraft:smithing", "block.minecraft.smithing_table",
            "minecraft:stonecutting", "block.minecraft.stonecutter");

    private VanillaTypeNames() {}

    static Optional<String> of(String typeId) {
        return Optional.ofNullable(KEYS.get(typeId)).flatMap(key -> TypeNames.of(Component.translatable(key)));
    }
}
