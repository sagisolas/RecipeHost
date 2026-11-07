package org.sagisolas.recipehost.mod.snapshot;

import org.junit.Test;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.Slot;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class RecipeMapperTest {

    private static final Slot E = new Slot.Builder().build();
    private static final Slot A = slotOf("minecraft:a");
    private static final Slot B = slotOf("minecraft:b");
    private static final Slot C = slotOf("minecraft:c");

    private static Slot slotOf(String itemId) {
        return new Slot.Builder().stacks(List.of(new ItemStack.Builder().item_id(itemId).count(1).build())).build();
    }

    @Test
    public void aTwoByTwoPatternSitsTopLeftAndKeepsItsEmptyCell() {
        assertEquals(
                List.of(A, E, E,
                        B, C, E,
                        E, E, E),
                RecipeMapper.craftingGrid(List.of(A, E, B, C), 2, 2));
    }

    @Test
    public void aOneWidePatternIsCentredInTheGridLikeTheRecipeBookPlacesIt() {
        assertEquals(
                List.of(E, A, E,
                        E, B, E,
                        E, E, E),
                RecipeMapper.craftingGrid(List.of(A, B), 1, 2));
        assertEquals(
                List.of(E, E, E,
                        E, A, E,
                        E, E, E),
                RecipeMapper.craftingGrid(List.of(A), 1, 1));
    }

    @Test
    public void aThreeByThreePatternFillsTheGridAsItIs() {
        List<Slot> pattern = List.of(A, B, C, C, E, A, B, A, C);

        assertEquals(pattern, RecipeMapper.craftingGrid(pattern, 3, 3));
    }
}
