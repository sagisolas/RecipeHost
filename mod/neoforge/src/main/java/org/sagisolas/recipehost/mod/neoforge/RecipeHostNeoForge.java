/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.sagisolas.recipehost.mod.RecipeHost;

@Mod(value = RecipeHost.MOD_ID, dist = Dist.CLIENT)
public final class RecipeHostNeoForge {

    public RecipeHostNeoForge() {
        RecipeHost.init();
    }
}
