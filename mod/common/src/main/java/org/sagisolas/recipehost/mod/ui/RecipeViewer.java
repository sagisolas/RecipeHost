/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.ui;

import java.util.List;
import org.sagisolas.recipehost.mod.ModDescriptor;

/** The recipe viewer mod whose corner buttons the pairing button sits next to and imitates. */
public enum RecipeViewer {
    EMI,
    JEI,
    NONE;

    /** EMI wins when both are loaded: it's also the recipe source then (ADR 0008). */
    public static RecipeViewer detect(List<ModDescriptor> mods) {
        if (isLoaded(mods, "emi")) return EMI;
        if (isLoaded(mods, "jei")) return JEI;
        return NONE;
    }

    private static boolean isLoaded(List<ModDescriptor> mods, String id) {
        return mods.stream().anyMatch(mod -> mod.id().equals(id));
    }
}
