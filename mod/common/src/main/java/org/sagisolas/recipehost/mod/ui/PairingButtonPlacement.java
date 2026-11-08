package org.sagisolas.recipehost.mod.ui;

/**
 * Top-left corner of the 20x20 pairing button. It sits in the bottom-left corner of the
 * screen, or right of EMI's config and recipe-tree buttons when EMI is loaded. EMI draws
 * those at fixed spots ({@code x=2} and {@code x=24}, {@code y=height-22}) and doesn't
 * expose them as screen widgets, so they can't be found at runtime.
 */
record PairingButtonPlacement(int x, int y) {

    static final int SIZE = 20;
    private static final int MARGIN = 2;
    private static final int EMI_BUTTONS = 2;

    static PairingButtonPlacement of(int screenHeight, boolean emiLoaded) {
        int x = MARGIN + (emiLoaded ? EMI_BUTTONS * (SIZE + MARGIN) : 0);
        return new PairingButtonPlacement(x, screenHeight - SIZE - MARGIN);
    }
}
