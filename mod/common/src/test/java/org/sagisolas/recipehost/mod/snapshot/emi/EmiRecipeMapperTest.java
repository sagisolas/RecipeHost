/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.emi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.Slot;

public class EmiRecipeMapperTest {

    private static final EmiRecipeMapper.Stack STICK = new EmiRecipeMapper.Stack("minecraft:stick", 1);
    private static final EmiRecipeMapper.Stack TORCH = new EmiRecipeMapper.Stack("minecraft:torch", 4);
    private static final EmiRecipeMapper.Stack WATER = new EmiRecipeMapper.Stack(null, 1000);

    private static ItemStack wire(String itemId, int count) {
        return ItemStack.newBuilder().setItemId(itemId).setCount(count).build();
    }

    @Test
    public void aRecipeWithoutAnIdGetsTheSameFabricatedIdForTheSameBody() {
        Recipe first = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(STICK)), List.of(TORCH));
        Recipe again = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(STICK)), List.of(TORCH));

        assertTrue(first.getId(), first.getId().matches("create:[0-9a-f]{32}"));
        assertEquals(first.getId(), again.getId());
    }

    @Test
    public void aDifferentBodyGetsADifferentFabricatedId() {
        Recipe sticks = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(STICK)), List.of(TORCH));
        Recipe torches = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(TORCH)), List.of(TORCH));

        assertNotEquals(sticks.getId(), torches.getId());
    }

    @Test
    public void anIdFromEmiIsKeptAsItIsSyntheticOnesIncluded() {
        assertEquals(
                "minecraft:torch",
                EmiRecipeMapper.map("minecraft:torch", "minecraft:crafting", List.of(List.of(STICK)), List.of(TORCH))
                        .getId());
        assertEquals(
                "emi:/brewing/x",
                EmiRecipeMapper.map("emi:/brewing/x", "minecraft:brewing", List.of(List.of(STICK)), List.of(TORCH))
                        .getId());
    }

    @Test
    public void onlyItemStacksAreKeptAndAnInputWithNoItemsIsDropped() {
        Recipe recipe = EmiRecipeMapper.map(
                "create:mixing/x",
                "create:mixing",
                List.of(List.of(WATER), List.of(WATER, STICK)),
                List.of(WATER, TORCH));

        assertEquals(
                List.of(Slot.newBuilder()
                        .addAllStacks(List.of(wire("minecraft:stick", 1)))
                        .build()),
                recipe.getInputsList());
        assertEquals(List.of(wire("minecraft:torch", 4)), recipe.getResultsList());
    }

    @Test
    public void anAmountTooLargeForTheWireCountIsCappedRatherThanWrapped() {
        Recipe recipe = EmiRecipeMapper.map(
                "x:y", "x:z", List.of(), List.of(new EmiRecipeMapper.Stack("minecraft:torch", 5_000_000_000L)));

        assertEquals(List.of(wire("minecraft:torch", Integer.MAX_VALUE)), recipe.getResultsList());
    }

    @Test
    public void everyLayoutSlotGetsAnEntryWithItsItemsAndAFluidSlotStaysEmpty() {
        List<Slot> stacks = EmiRecipeMapper.layoutStacks(List.of(List.of(STICK), List.of(WATER), List.of(TORCH)));

        assertEquals(
                List.of(
                        Slot.newBuilder()
                                .addAllStacks(List.of(wire("minecraft:stick", 1)))
                                .build(),
                        Slot.newBuilder().build(),
                        Slot.newBuilder()
                                .addAllStacks(List.of(wire("minecraft:torch", 4)))
                                .build()),
                stacks);
    }
}
