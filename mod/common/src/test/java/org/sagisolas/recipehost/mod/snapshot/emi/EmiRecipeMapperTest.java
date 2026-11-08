package org.sagisolas.recipehost.mod.snapshot.emi;

import org.junit.Test;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.Slot;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class EmiRecipeMapperTest {

    private static final EmiRecipeMapper.Stack STICK = new EmiRecipeMapper.Stack("minecraft:stick", 1);
    private static final EmiRecipeMapper.Stack TORCH = new EmiRecipeMapper.Stack("minecraft:torch", 4);
    private static final EmiRecipeMapper.Stack WATER = new EmiRecipeMapper.Stack(null, 1000);

    private static ItemStack wire(String itemId, int count) {
        return new ItemStack.Builder().item_id(itemId).count(count).build();
    }

    @Test
    public void aRecipeWithoutAnIdGetsTheSameFabricatedIdForTheSameBody() {
        Recipe first = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(STICK)), List.of(TORCH));
        Recipe again = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(STICK)), List.of(TORCH));

        assertTrue(first.id, first.id.matches("create:[0-9a-f]{32}"));
        assertEquals(first.id, again.id);
    }

    @Test
    public void aDifferentBodyGetsADifferentFabricatedId() {
        Recipe sticks = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(STICK)), List.of(TORCH));
        Recipe torches = EmiRecipeMapper.map(null, "create:mixing", List.of(List.of(TORCH)), List.of(TORCH));

        assertNotEquals(sticks.id, torches.id);
    }

    @Test
    public void anIdFromEmiIsKeptAsItIsSyntheticOnesIncluded() {
        assertEquals("minecraft:torch",
                EmiRecipeMapper.map("minecraft:torch", "minecraft:crafting", List.of(List.of(STICK)), List.of(TORCH)).id);
        assertEquals("emi:/brewing/x",
                EmiRecipeMapper.map("emi:/brewing/x", "minecraft:brewing", List.of(List.of(STICK)), List.of(TORCH)).id);
    }

    @Test
    public void onlyItemStacksAreKeptAndAnInputWithNoItemsIsDropped() {
        Recipe recipe = EmiRecipeMapper.map("create:mixing/x", "create:mixing",
                List.of(List.of(WATER), List.of(WATER, STICK)),
                List.of(WATER, TORCH));

        assertEquals(List.of(new Slot.Builder().stacks(List.of(wire("minecraft:stick", 1))).build()), recipe.inputs);
        assertEquals(List.of(wire("minecraft:torch", 4)), recipe.results);
    }

    @Test
    public void anAmountTooLargeForTheWireCountIsCappedRatherThanWrapped() {
        Recipe recipe = EmiRecipeMapper.map("x:y", "x:z",
                List.of(), List.of(new EmiRecipeMapper.Stack("minecraft:torch", 5_000_000_000L)));

        assertEquals(List.of(wire("minecraft:torch", Integer.MAX_VALUE)), recipe.results);
    }
}
