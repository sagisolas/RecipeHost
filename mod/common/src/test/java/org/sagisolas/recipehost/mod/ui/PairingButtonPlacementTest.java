package org.sagisolas.recipehost.mod.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PairingButtonPlacementTest {

    @Test
    public void withoutEmiSitsInBottomLeftCorner() {
        assertEquals(new PairingButtonPlacement(2, 218), PairingButtonPlacement.of(240, false));
    }

    @Test
    public void withEmiSitsRightOfEmiConfigAndTreeButtons() {
        // EMI draws its 20x20 buttons at x=2 and x=24, y=height-22.
        assertEquals(new PairingButtonPlacement(46, 218), PairingButtonPlacement.of(240, true));
    }
}
