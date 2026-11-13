/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.ui;

/**
 * Top-left corner of the 20x20 pairing button, next to the recipe viewer's own bottom-left
 * buttons. Neither viewer exposes those as screen widgets, so their spots are mirrored here:
 * <ul>
 *   <li>EMI: config and recipe-tree buttons at {@code x=2} and {@code x=24},
 *       {@code y=height-22}. The pairing button sits above the config button.
 *   <li>JEI: bookmark and lookup-history buttons at {@code x=6} and {@code x=28},
 *       {@code y=height-26}. The pairing button sits right of them, in the strip JEI keeps free
 *       of bookmarks. JEI aligns its buttons with the bookmark grid instead when the grid has
 *       room, which is also {@code x=6} with the default left alignment.
 *   <li>None: the bottom-left corner.
 * </ul>
 * Not handled: a left area too narrow for the row (JEI then hides its buttons; ours may overlap
 * the container screen).
 */
record PairingButtonPlacement(int x, int y) {

    static final int SIZE = 20;
    private static final int GAP = 2;
    private static final int JEI_MARGIN = 6;
    private static final int JEI_BUTTONS = 2;

    static PairingButtonPlacement of(int screenHeight, RecipeViewer viewer) {
        return switch (viewer) {
            case EMI -> new PairingButtonPlacement(GAP, screenHeight - 2 * (SIZE + GAP));
            case JEI ->
                new PairingButtonPlacement(JEI_MARGIN + JEI_BUTTONS * (SIZE + GAP), screenHeight - SIZE - JEI_MARGIN);
            case NONE -> new PairingButtonPlacement(GAP, screenHeight - SIZE - GAP);
        };
    }
}
