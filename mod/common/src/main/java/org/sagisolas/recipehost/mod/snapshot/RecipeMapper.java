package org.sagisolas.recipehost.mod.snapshot;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flat {@code instanceof} cascade mapping vanilla recipe types to the wire
 * {@link org.sagisolas.recipehost.protocol.Recipe}. Anything unrecognized becomes
 * a shell carrying only {@code type_id} and the result (ADR 0008). Pinned
 * {@code extras} keys are documented in ADR 0002.
 */
final class RecipeMapper {

    private RecipeMapper() {}

    static org.sagisolas.recipehost.protocol.Recipe toWire(RecipeHolder<?> holder, RegistryAccess registryAccess) {
        Recipe<?> recipe = holder.value();
        String typeId = typeIdOf(recipe);

        if (recipe instanceof ShapedRecipe shaped) {
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("width", String.valueOf(shaped.getWidth()));
            extras.put("height", String.valueOf(shaped.getHeight()));
            return wire(typeId, slotsOf(shaped.getIngredients()), shaped.getResultItem(registryAccess), extras);
        }

        if (recipe instanceof ShapelessRecipe shapeless) {
            return wire(typeId, slotsOf(shapeless.getIngredients()), shapeless.getResultItem(registryAccess), Map.of());
        }

        if (recipe instanceof AbstractCookingRecipe cooking) {
            // 1.21.1 doesn't expose a public input() accessor; getIngredients() returns a size-1 list.
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("cook_time", String.valueOf(cooking.getCookingTime()));
            extras.put("xp", String.valueOf(cooking.getExperience()));
            return wire(typeId, slotsOf(cooking.getIngredients()), cooking.getResultItem(registryAccess), extras);
        }

        if (recipe instanceof SmithingTransformRecipe smithing) {
            // template/base/addition fields are package-private in 1.21.1; getIngredients()
            // is the override that returns them in that documented order.
            return wire(typeId, slotsOf(smithing.getIngredients()), smithing.getResultItem(registryAccess), Map.of());
        }

        if (recipe instanceof StonecutterRecipe stonecutter) {
            // Same accessor-visibility caveat as AbstractCookingRecipe.
            return wire(typeId, slotsOf(stonecutter.getIngredients()), stonecutter.getResultItem(registryAccess), Map.of());
        }

        // Shell for unknown types.
        return wire(typeId, List.of(), recipe.getResultItem(registryAccess), Map.of());
    }

    private static String typeIdOf(Recipe<?> recipe) {
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        // A modded RecipeType that isn't registered through the vanilla registry
        // returns null; ship the shell anyway with a synthetic id so US11 still
        // holds (the recipe appears in the catalog with its result).
        return key != null ? key.toString() : "unknown:" + recipe.getClass().getName();
    }

    private static List<Slot> slotsOf(Iterable<Ingredient> ingredients) {
        List<Slot> slots = new ArrayList<>();
        for (Ingredient ing : ingredients) {
            slots.add(IngredientMapper.toSlot(ing));
        }
        return slots;
    }

    private static org.sagisolas.recipehost.protocol.Recipe wire(
            String typeId,
            List<Slot> inputs,
            net.minecraft.world.item.ItemStack result,
            Map<String, String> extras) {
        return new org.sagisolas.recipehost.protocol.Recipe.Builder()
                .type_id(typeId)
                .inputs(inputs)
                .results(List.of(new ItemStack.Builder()
                        .item_id(Ids.itemId(result.getItem()))
                        .count(result.getCount())
                        .build()))
                .extras(extras)
                .build();
    }
}
