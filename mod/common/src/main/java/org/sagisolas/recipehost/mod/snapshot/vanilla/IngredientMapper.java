package org.sagisolas.recipehost.mod.snapshot.vanilla;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.sagisolas.recipehost.protocol.Slot;

import java.util.ArrayList;
import java.util.List;
import org.sagisolas.recipehost.mod.snapshot.item.Ids;

final class IngredientMapper {

    private IngredientMapper() {}

    static Slot toSlot(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        List<org.sagisolas.recipehost.protocol.ItemStack> stacks = new ArrayList<>(items.length);
        for (ItemStack stack : items) {
            stacks.add(new org.sagisolas.recipehost.protocol.ItemStack.Builder()
                    .item_id(Ids.itemId(stack.getItem()))
                    .count(stack.getCount())
                    .build());
        }
        return new Slot.Builder().stacks(stacks).build();
    }
}
