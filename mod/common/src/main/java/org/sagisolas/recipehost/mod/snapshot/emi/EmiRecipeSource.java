package org.sagisolas.recipehost.mod.snapshot.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.sagisolas.recipehost.mod.snapshot.RecipeSource;
import org.sagisolas.recipehost.mod.snapshot.item.Ids;
import org.sagisolas.recipehost.mod.snapshot.recipe.LaidOut;
import org.sagisolas.recipehost.mod.snapshot.recipe.RecipeCandidate;
import org.sagisolas.recipehost.protocol.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every recipe EMI knows, vanilla and JEI-only ones included (ADR 0008), each laid out from its
 * captured widgets (ADR 0015). Only constructed when EMI is loaded, so EMI's classes are never
 * reached without it.
 */
public final class EmiRecipeSource implements RecipeSource {

    @Override
    public List<RecipeCandidate> candidates() {
        return EmiApi.getRecipeManager().getRecipes().stream().<RecipeCandidate>map(EmiCandidate::new).toList();
    }

    /** One EMI recipe: read from its inputs and outputs, laid out by capturing its widgets. */
    private record EmiCandidate(EmiRecipe recipe) implements RecipeCandidate {

        @Override
        public String typeId() {
            return recipe.getCategory().getId().toString();
        }

        @Override
        public Recipe read() {
            ResourceLocation id = recipe.getId();
            return EmiRecipeMapper.map(id == null ? null : id.toString(), typeId(),
                    inputsOf(recipe), stacksOf(recipe.getOutputs()));
        }

        /** Runs {@code addWidgets} where mods expect it, on the render thread. */
        @Override
        public Optional<LaidOut> layOut(Recipe read) {
            CapturingWidgetHolder holder = new CapturingWidgetHolder(
                    recipe.getDisplayWidth(), recipe.getDisplayHeight(), recipe.getCatalysts());
            recipe.addWidgets(holder);
            CapturingWidgetHolder.Capture capture = holder.capture();
            // A recipe with nothing to show has no layout.
            if (capture.width() <= 0 || capture.height() <= 0) return Optional.empty();
            List<List<EmiRecipeMapper.Stack>> slotStacks = capture.slots().stream().map(slot -> stacksOf(slot.stack())).toList();
            return Optional.of(new LaidOut(new EmiLayoutDrawer(capture).job(typeId(), read.getId()),
                    EmiRecipeMapper.layoutStacks(slotStacks)));
        }
    }

    private static List<List<EmiRecipeMapper.Stack>> inputsOf(EmiRecipe recipe) {
        List<List<EmiRecipeMapper.Stack>> inputs = new ArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            inputs.add(stacksOf(ingredient));
        }
        return inputs;
    }

    /** getEmiStacks() expands tags into their items. */
    private static List<EmiRecipeMapper.Stack> stacksOf(EmiIngredient ingredient) {
        return stacksOf(ingredient.getEmiStacks());
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
