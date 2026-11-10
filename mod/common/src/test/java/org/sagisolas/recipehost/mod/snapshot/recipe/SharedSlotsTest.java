package org.sagisolas.recipehost.mod.snapshot.recipe;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.RecipeBatch;
import org.sagisolas.recipehost.protocol.Slot;

public class SharedSlotsTest {

    private static final Slot PLANKS = slotOf("minecraft:oak_planks", "minecraft:spruce_planks");
    private static final Slot STICK = slotOf("minecraft:stick");

    @Test
    public void aSlotWithAlternativesThatRepeatsInTheBatchIsSentOnce() {
        Recipe chest = Recipe.newBuilder()
                .setId("minecraft:chest")
                .addInputs(PLANKS)
                .addInputs(PLANKS)
                .addLayoutStacks(PLANKS)
                .build();
        Recipe stick =
                Recipe.newBuilder().setId("minecraft:stick").addInputs(PLANKS).build();

        RecipeBatch batch = SharedSlots.batchOf(List.of(chest, stick));

        Slot shared = Slot.newBuilder().setShared(1).build();
        assertEquals(List.of(PLANKS), batch.getSharedSlotsList());
        assertEquals(List.of(shared, shared), batch.getRecipes(0).getInputsList());
        assertEquals(List.of(shared), batch.getRecipes(0).getLayoutStacksList());
        assertEquals(List.of(shared), batch.getRecipes(1).getInputsList());
    }

    @Test
    public void aSingleItemSlotOrOneSeenOnceStaysAsItIs() {
        Slot once = slotOf("minecraft:iron_ingot", "minecraft:gold_ingot");
        Recipe a = Recipe.newBuilder()
                .setId("minecraft:a")
                .addInputs(STICK)
                .addInputs(once)
                .build();
        Recipe b = Recipe.newBuilder().setId("minecraft:b").addInputs(STICK).build();

        RecipeBatch batch = SharedSlots.batchOf(List.of(a, b));

        assertEquals(List.of(), batch.getSharedSlotsList());
        assertEquals(List.of(a, b), batch.getRecipesList());
    }

    private static Slot slotOf(String... itemIds) {
        Slot.Builder slot = Slot.newBuilder();
        for (String id : itemIds)
            slot.addStacks(ItemStack.newBuilder().setItemId(id).setCount(1));
        return slot.build();
    }
}
