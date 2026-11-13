/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.sagisolas.recipehost.mod.RecipeHost;

public final class RecipeHostFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RecipeHost.init();
    }
}
