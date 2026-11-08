package org.sagisolas.recipehost.mod.snapshot.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.sagisolas.recipehost.mod.snapshot.RecipeSource;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.Recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The level's {@code RecipeManager}, mapped by the vanilla dispatch, with the companion mod's own layouts. */
public final class VanillaRecipeSource implements RecipeSource {

    private final ClientLevel level;

    public VanillaRecipeSource(ClientLevel level) {
        this.level = level;
    }

    @Override
    public Recipes collect() {
        RegistryAccess registryAccess = level.registryAccess();
        RecipeMapper mapper = new RecipeMapper(new VanillaLayouts(Minecraft.getInstance()));
        List<Recipe> recipes = new ArrayList<>();
        List<LayoutJob> layouts = new ArrayList<>();
        Set<String> namespaces = new HashSet<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            RecipeMapper.Mapped mapped = mapper.map(holder, registryAccess);
            recipes.add(mapped.recipe());
            mapped.layout().ifPresent(layouts::add);
            namespaces.add(holder.id().getNamespace());
        }
        return new Recipes(recipes, layouts, namespaces);
    }
}
