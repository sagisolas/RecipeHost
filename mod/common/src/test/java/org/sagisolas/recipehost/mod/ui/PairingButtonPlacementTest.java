package org.sagisolas.recipehost.mod.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PairingButtonPlacementTest {

    @Test
    public void withoutRecipeViewerSitsInBottomLeftCorner() {
        assertEquals(new PairingButtonPlacement(2, 218), PairingButtonPlacement.of(240, RecipeViewer.NONE));
    }

    @Test
    public void withEmiSitsAboveEmiConfigButton() {
        // EMI draws its 20x20 config button at x=2, y=height-22.
        assertEquals(new PairingButtonPlacement(2, 196), PairingButtonPlacement.of(240, RecipeViewer.EMI));
    }

    @Test
    public void withJeiSitsRightOfJeiBookmarkAndHistoryButtons() {
        // JEI draws its 20x20 buttons at x=6 and x=28, y=height-26.
        assertEquals(new PairingButtonPlacement(50, 214), PairingButtonPlacement.of(240, RecipeViewer.JEI));
    }
}
