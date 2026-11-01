package org.sagisolas.recipehost.mod.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.sagisolas.recipehost.mod.common.RecipeHost;

@Mod(value = RecipeHost.MOD_ID, dist = Dist.CLIENT)
public final class RecipeHostNeoForge {

    public RecipeHostNeoForge() {
        RecipeHost.init();
    }
}
