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
     * @param inputs     each input's alternatives, in order
     */
    static Recipe map(@Nullable String id, String categoryId, List<List<Stack>> inputs, List<Stack> outputs) {
        List<Slot> slots = new ArrayList<>(inputs.size());
        for (List<Stack> input : inputs) {
            List<ItemStack> items = itemStacks(input);
            // An input with no items (a fluid) is visible only in the layout (ADR 0015).
            if (!items.isEmpty()) slots.add(Slot.newBuilder().addAllStacks(items).build());
        }
        Recipe body = Recipe.newBuilder()
                .setTypeId(categoryId)
                .addAllInputs(slots)
                .addAllResults(itemStacks(outputs))
                .build();
        return body.toBuilder().setId(id != null ? id : fabricatedId(categoryId, body)).build();
    }

    /**
     * What each `Recipe slot` of a layout shows, in slot order. A slot that shows no item (a fluid
     * tank) keeps an empty entry, so entries line up with slots.
     */
    static List<Slot> layoutStacks(List<List<Stack>> slotStacks) {
        List<Slot> layoutSlots = new ArrayList<>(slotStacks.size());
        for (List<Stack> stacks : slotStacks) {
            layoutSlots.add(Slot.newBuilder().addAllStacks(itemStacks(stacks)).build());
        }
        return layoutSlots;
    }

    private static List<ItemStack> itemStacks(List<Stack> stacks) {
        List<ItemStack> items = new ArrayList<>(stacks.size());
        for (Stack stack : stacks) {
            if (stack.itemId() == null) continue;
            items.add(ItemStack.newBuilder().setItemId(stack.itemId()).setCount((int) Math.min(stack.amount(), Integer.MAX_VALUE)).build());
        }
        return items;
    }

    /**
     * {@code <category namespace>:<hash>}, the same for the same body in every Scan (ADR 0011):
     * SHA-256 over the encoded body without an id, truncated to 128 bits and hex-encoded.
     */
    private static String fabricatedId(String categoryId, Recipe body) {
        return namespaceOf(categoryId) + ":" + Hashes.truncatedSha256Hex(body.toByteArray());
    }

    /** The namespace of a namespaced id; {@code minecraft} when it has none. */
    private static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }
}
