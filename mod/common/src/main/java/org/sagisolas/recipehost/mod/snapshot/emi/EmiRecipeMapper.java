package org.sagisolas.recipehost.mod.snapshot.emi;

import org.jetbrains.annotations.Nullable;
import org.sagisolas.recipehost.mod.snapshot.Hashes;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the wire {@link Recipe} for one EMI recipe from plain views of its stacks, so the
 * rules live apart from EMI's and Minecraft's types (ADR 0008, ADR 0015).
 */
final class EmiRecipeMapper {

    private EmiRecipeMapper() {}

    /** One EMI stack: its item id, or null when it isn't an item (a fluid, a chemical). */
    record Stack(@Nullable String itemId, long amount) {}

    /**
     * @param id         the recipe's id as EMI gives it, or null
     * @param categoryId the id of the recipe's EMI category, which becomes its {@code type_id}
     * @param inputs       each input's alternatives, in order
     * @param layoutStacks what each `Recipe slot` of the recipe's layout shows, in slot order;
     *                     empty when it has no layout
     */
    static Recipe map(@Nullable String id, String categoryId, List<List<Stack>> inputs, List<Stack> outputs,
                      List<List<Stack>> layoutStacks) {
        List<Slot> slots = new ArrayList<>(inputs.size());
        for (List<Stack> input : inputs) {
            List<ItemStack> items = itemStacks(input);
            // An input with no items (a fluid) is visible only in the layout (ADR 0015).
            if (!items.isEmpty()) slots.add(new Slot.Builder().stacks(items).build());
        }
        Recipe body = new Recipe.Builder()
                .type_id(categoryId)
                .inputs(slots)
                .results(itemStacks(outputs))
                .build();
        // A slot that shows no item (a fluid tank) keeps its entry, so entries line up with slots.
        List<Slot> layoutSlots = new ArrayList<>(layoutStacks.size());
        for (List<Stack> stacks : layoutStacks) {
            layoutSlots.add(new Slot.Builder().stacks(itemStacks(stacks)).build());
        }
        // Hashed without layout_stacks, so a recipe's id doesn't depend on whether its layout drew.
        return body.newBuilder()
                .id(id != null ? id : fabricatedId(categoryId, body))
                .layout_stacks(layoutSlots)
                .build();
    }

    private static List<ItemStack> itemStacks(List<Stack> stacks) {
        List<ItemStack> items = new ArrayList<>(stacks.size());
        for (Stack stack : stacks) {
            if (stack.itemId() == null) continue;
            items.add(new ItemStack.Builder().item_id(stack.itemId()).count((int) Math.min(stack.amount(), Integer.MAX_VALUE)).build());
        }
        return items;
    }

    /**
     * {@code <category namespace>:<hash>}, the same for the same body in every Scan (ADR 0011):
     * SHA-256 over the encoded body without an id, truncated to 128 bits and hex-encoded.
     */
    private static String fabricatedId(String categoryId, Recipe body) {
        return namespaceOf(categoryId) + ":" + Hashes.truncatedSha256Hex(Recipe.ADAPTER.encode(body));
    }

    /** The namespace of a namespaced id; {@code minecraft} when it has none. */
    private static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }
}
