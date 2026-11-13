/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.vanilla;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.sagisolas.recipehost.mod.snapshot.item.Ids;
import org.sagisolas.recipehost.protocol.Slot;

final class IngredientMapper {

    private IngredientMapper() {}

    static Slot toSlot(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        List<org.sagisolas.recipehost.protocol.ItemStack> stacks = new ArrayList<>(items.length);
        for (ItemStack stack : items) {
            stacks.add(org.sagisolas.recipehost.protocol.ItemStack.newBuilder()
                    .setItemId(Ids.itemId(stack.getItem()))
                    .setCount(stack.getCount())
                    .build());
        }
        return Slot.newBuilder().addAllStacks(stacks).build();
    }
}
