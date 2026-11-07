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
import org.sagisolas.recipehost.protocol.RecipeSlot;
import org.sagisolas.recipehost.protocol.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Flat {@code instanceof} cascade mapping vanilla recipe types to the wire
 * {@link org.sagisolas.recipehost.protocol.Recipe}. Anything unrecognized becomes
 * a shell carrying only {@code type_id} and the result (ADR 0008). Pinned
 * {@code extras} keys are documented in ADR 0002.
 */
final class RecipeMapper {

    private static final Slot EMPTY = new Slot.Builder().build();
    /** The crafting table's grid is 3×3. */
    private static final int GRID_SIDE = 3;

    /** A wire recipe, and the job that renders its layout when it has one. */
    record Mapped(org.sagisolas.recipehost.protocol.Recipe recipe, Optional<LayoutJob> layout) {}

    private final VanillaLayouts layouts;

    RecipeMapper(VanillaLayouts layouts) {
        this.layouts = layouts;
    }

    Mapped map(RecipeHolder<?> holder, RegistryAccess registryAccess) {
        Recipe<?> recipe = holder.value();
        String id = holder.id().toString();
        String typeId = typeIdOf(recipe);

        if (recipe instanceof ShapedRecipe shaped) {
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("width", String.valueOf(shaped.getWidth()));
            extras.put("height", String.valueOf(shaped.getHeight()));
            List<Slot> inputs = slotsOf(shaped.getIngredients());
            // A modded subclass may be larger than the crafting table's grid; it gets no layout.
            boolean fits = shaped.getWidth() <= GRID_SIDE && shaped.getHeight() <= GRID_SIDE;
            return mapped(id, typeId, inputs, shaped.getResultItem(registryAccess), extras,
                    fits ? layouts.crafting() : null,
                    fits ? craftingGrid(inputs, shaped.getWidth(), shaped.getHeight()) : List.of());
        }

        if (recipe instanceof ShapelessRecipe shapeless) {
            List<Slot> inputs = slotsOf(shapeless.getIngredients());
            boolean fits = inputs.size() <= GRID_SIDE * GRID_SIDE;
            return mapped(id, typeId, inputs, shapeless.getResultItem(registryAccess), Map.of(),
                    fits ? layouts.crafting() : null, inputs);
        }

        if (recipe instanceof AbstractCookingRecipe cooking) {
            // 1.21.1 doesn't expose a public input() accessor; getIngredients() returns a size-1 list.
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("cook_time", String.valueOf(cooking.getCookingTime()));
            extras.put("xp", String.valueOf(cooking.getExperience()));
            List<Slot> inputs = slotsOf(cooking.getIngredients());
            return mapped(id, typeId, inputs, cooking.getResultItem(registryAccess), extras,
                    layouts.cooking(typeId, cooking.getCookingTime(), cooking.getExperience()), inputs);
        }

        if (recipe instanceof SmithingTransformRecipe smithing) {
            // template/base/addition fields are package-private in 1.21.1; getIngredients()
            // is the override that returns them in that documented order.
            List<Slot> inputs = slotsOf(smithing.getIngredients());
            return mapped(id, typeId, inputs, smithing.getResultItem(registryAccess), Map.of(), layouts.smithing(), inputs);
        }

        if (recipe instanceof StonecutterRecipe stonecutter) {
            // Same accessor-visibility caveat as AbstractCookingRecipe.
            List<Slot> inputs = slotsOf(stonecutter.getIngredients());
            return mapped(id, typeId, inputs, stonecutter.getResultItem(registryAccess), Map.of(), layouts.stonecutter(), inputs);
        }

        // Shell for unknown types: no inputs, so no layout.
        return mapped(id, typeId, List.of(), recipe.getResultItem(registryAccess), Map.of(), null, List.of());
    }

    /**
     * A shaped recipe's {@code width × height} pattern (its ingredients row by row,
     * empty cells included) placed in the crafting table's 3×3 grid, row by row. Like
     * the vanilla recipe book, a pattern narrower or shorter than the grid is centred
     * along that axis, rounding towards the top left: a 1×2 sits in the middle column,
     * a 2×2 in the top-left corner.
     */
    static List<Slot> craftingGrid(List<Slot> pattern, int width, int height) {
        int columnOffset = (GRID_SIDE - width) / 2;
        int rowOffset = (GRID_SIDE - height) / 2;
        List<Slot> grid = new ArrayList<>(GRID_SIDE * GRID_SIDE);
        for (int row = 0; row < GRID_SIDE; row++) {
            for (int column = 0; column < GRID_SIDE; column++) {
                int patternRow = row - rowOffset;
                int patternColumn = column - columnOffset;
                boolean inPattern = patternRow >= 0 && patternRow < height && patternColumn >= 0 && patternColumn < width;
                grid.add(inPattern ? pattern.get(patternRow * width + patternColumn) : EMPTY);
            }
        }
        return grid;
    }

    /**
     * One entry per layout slot, in slot order: input slots take {@code inputs} in
     * order (an input slot past the last input stays empty, as do catalyst slots),
     * and output slots take {@code result}.
     */
    static List<Slot> layoutStacks(List<RecipeSlot> slots, List<Slot> inputs, Slot result) {
        List<Slot> stacks = new ArrayList<>(slots.size());
        int nextInput = 0;
        for (RecipeSlot slot : slots) {
            switch (slot.role) {
                case INPUT -> stacks.add(nextInput < inputs.size() ? inputs.get(nextInput++) : EMPTY);
                case OUTPUT -> stacks.add(result);
                case CATALYST -> stacks.add(EMPTY);
            }
        }
        return stacks;
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

    /**
     * @param layout      the recipe's layout, or null for none
     * @param layoutInputs what the layout's input slots show, in order: {@code inputs}
     *                    themselves, or a shaped recipe's {@link #craftingGrid}
     */
    private static Mapped mapped(
            String id,
            String typeId,
            List<Slot> inputs,
            net.minecraft.world.item.ItemStack result,
            Map<String, String> extras,
            VanillaLayout layout,
            List<Slot> layoutInputs) {
        ItemStack resultStack = new ItemStack.Builder()
                .item_id(Ids.itemId(result.getItem()))
                .count(result.getCount())
                .build();
        org.sagisolas.recipehost.protocol.Recipe wire = new org.sagisolas.recipehost.protocol.Recipe.Builder()
                .id(id)
                .type_id(typeId)
                .inputs(inputs)
                .results(List.of(resultStack))
                .extras(extras)
                .layout_stacks(layout == null ? List.of()
                        : layoutStacks(layout.slots(), layoutInputs, new Slot.Builder().stacks(List.of(resultStack)).build()))
                .build();
        return new Mapped(wire, Optional.ofNullable(layout).map(l -> l.job(typeId, id)));
    }
}
