package org.sagisolas.recipehost.mod.snapshot.vanilla;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.ItemStack;
import org.sagisolas.recipehost.protocol.RecipeSlot;
import org.sagisolas.recipehost.protocol.Slot;

public class RecipeMapperTest {

    private static final Slot E = Slot.newBuilder().build();
    private static final Slot A = slotOf("minecraft:a");
    private static final Slot B = slotOf("minecraft:b");
    private static final Slot C = slotOf("minecraft:c");

    private static Slot slotOf(String itemId) {
        return Slot.newBuilder()
                .addAllStacks(List.of(
                        ItemStack.newBuilder().setItemId(itemId).setCount(1).build()))
                .build();
    }

    @Test
    public void aTwoByTwoPatternSitsTopLeftAndKeepsItsEmptyCell() {
        assertEquals(List.of(A, E, E, B, C, E, E, E, E), RecipeMapper.craftingGrid(List.of(A, E, B, C), 2, 2));
    }

    @Test
    public void aOneWidePatternIsCentredInTheGridLikeTheRecipeBookPlacesIt() {
        assertEquals(List.of(E, A, E, E, B, E, E, E, E), RecipeMapper.craftingGrid(List.of(A, B), 1, 2));
        assertEquals(List.of(E, E, E, E, A, E, E, E, E), RecipeMapper.craftingGrid(List.of(A), 1, 1));
    }

    @Test
    public void aThreeByThreePatternFillsTheGridAsItIs() {
        List<Slot> pattern = List.of(A, B, C, C, E, A, B, A, C);

        assertEquals(pattern, RecipeMapper.craftingGrid(pattern, 3, 3));
    }

    private static RecipeSlot slot(RecipeSlot.Role role) {
        return RecipeSlot.newBuilder().setRole(role).build();
    }

    @Test
    public void inputSlotsTakeTheInputsInOrderAndOutputSlotsTheResult() {
        Slot result = slotOf("minecraft:result");

        assertEquals(
                List.of(A, B, result),
                RecipeMapper.layoutStacks(
                        List.of(slot(RecipeSlot.Role.INPUT), slot(RecipeSlot.Role.INPUT), slot(RecipeSlot.Role.OUTPUT)),
                        List.of(A, B),
                        result));
    }

    @Test
    public void anInputSlotPastTheLastInputAndACatalystSlotStayEmpty() {
        Slot result = slotOf("minecraft:result");

        assertEquals(
                List.of(A, E, E, result),
                RecipeMapper.layoutStacks(
                        List.of(
                                slot(RecipeSlot.Role.INPUT),
                                slot(RecipeSlot.Role.INPUT),
                                slot(RecipeSlot.Role.CATALYST),
                                slot(RecipeSlot.Role.OUTPUT)),
                        List.of(A),
                        result));
    }
}
