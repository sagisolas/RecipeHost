package org.sagisolas.recipehost.mod.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.sagisolas.recipehost.mod.common.RecipeHost;

public final class RecipeHostFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RecipeHost.init();
    }
}
