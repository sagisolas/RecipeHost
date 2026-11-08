package org.sagisolas.recipehost.mod.snapshot.emi;

import net.minecraft.resources.ResourceLocation;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmiRecipeSourceTest {

    @Test
    public void skipsEmiTagAndFuelCategories() {
        assertTrue(EmiRecipeSource.isSkippedCategory(ResourceLocation.parse("emi:tag")));
        assertTrue(EmiRecipeSource.isSkippedCategory(ResourceLocation.parse("emi:fuel")));
    }

    @Test
    public void skipsJeiTagCategoryOfEveryRegistry() {
        assertTrue(EmiRecipeSource.isSkippedCategory(ResourceLocation.parse("minecraft:tag_recipes/item")));
        assertTrue(EmiRecipeSource.isSkippedCategory(ResourceLocation.parse("minecraft:tag_recipes/block")));
        assertTrue(EmiRecipeSource.isSkippedCategory(ResourceLocation.parse("minecraft:tag_recipes/fluid")));
    }

    @Test
    public void keepsRecipeCategories() {
        assertFalse(EmiRecipeSource.isSkippedCategory(ResourceLocation.parse("minecraft:crafting")));
        assertFalse(EmiRecipeSource.isSkippedCategory(ResourceLocation.parse("create:tag_recipes/item")));
    }
}
