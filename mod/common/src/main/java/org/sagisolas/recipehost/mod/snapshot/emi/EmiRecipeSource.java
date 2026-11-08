package org.sagisolas.recipehost.mod.snapshot.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.sagisolas.recipehost.mod.snapshot.RecipeSource;
import org.sagisolas.recipehost.mod.snapshot.item.Ids;
import org.sagisolas.recipehost.protocol.Recipe;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every recipe EMI knows, vanilla and JEI-only ones included (ADR 0008). Only constructed
 * when EMI is loaded, so EMI's classes are never reached without it.
 */
public final class EmiRecipeSource implements RecipeSource {

    private static final Logger LOG = LogUtils.getLogger();

    @Override
    public Recipes collect() {
        List<Recipe> recipes = new ArrayList<>();
        Set<String> namespaces = new HashSet<>();
        Set<ResourceLocation> failedCategories = new HashSet<>();
        for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipes()) {
            ResourceLocation categoryId = recipe.getCategory().getId();
            ResourceLocation id;
            Recipe wire;
            try {
                id = recipe.getId();
                wire = EmiRecipeMapper.map(id == null ? null : id.toString(), categoryId.toString(),
                        inputsOf(recipe), stacksOf(recipe.getOutputs()));
            } catch (RuntimeException e) {
                // A mod's broken recipe costs only that recipe, not the Snapshot.
                if (failedCategories.add(categoryId)) {
                    LOG.warn("[recipehost] skipping EMI recipes of {} that fail to read", categoryId, e);
                }
                continue;
            }
            recipes.add(wire);
            // A recipe without an id gets one in its category's namespace.
            namespaces.add((id != null ? id : categoryId).getNamespace());
        }
        // TODO(recipe-layouts issue 05): EMI layout capture supplies the layout jobs.
        return new Recipes(recipes, List.of(), namespaces);
    }

    private static List<List<EmiRecipeMapper.Stack>> inputsOf(EmiRecipe recipe) {
        List<List<EmiRecipeMapper.Stack>> inputs = new ArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            // getEmiStacks() expands tags into their items.
            inputs.add(stacksOf(ingredient.getEmiStacks()));
        }
        return inputs;
    }

    private static List<EmiRecipeMapper.Stack> stacksOf(List<EmiStack> stacks) {
        List<EmiRecipeMapper.Stack> views = new ArrayList<>(stacks.size());
        for (EmiStack stack : stacks) {
            // ItemEmiStack isn't in EMI's API jar; an item stack is one keyed by its Item.
            String itemId = !stack.isEmpty() && stack.getKey() instanceof Item item ? Ids.itemId(item) : null;
            views.add(new EmiRecipeMapper.Stack(itemId, stack.getAmount()));
        }
        return views;
    }
}
