package org.sagisolas.recipehost.mod.ui;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;
import org.sagisolas.recipehost.mod.ModDescriptor;

public class RecipeViewerTest {

    @Test
    public void noneWithoutEmiOrJei() {
        assertEquals(RecipeViewer.NONE, RecipeViewer.detect(List.of(mod("minecraft"), mod("recipehost"))));
    }

    @Test
    public void emiWhenEmiLoaded() {
        assertEquals(RecipeViewer.EMI, RecipeViewer.detect(List.of(mod("emi"))));
    }

    @Test
    public void jeiWhenJeiLoaded() {
        assertEquals(RecipeViewer.JEI, RecipeViewer.detect(List.of(mod("jei"))));
    }

    @Test
    public void emiWinsWhenBothLoaded() {
        assertEquals(RecipeViewer.EMI, RecipeViewer.detect(List.of(mod("jei"), mod("emi"))));
    }

    private static ModDescriptor mod(String id) {
        return new ModDescriptor(id, id, "1.0");
    }
}
