package org.sagisolas.recipehost.mod.snapshot.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.sagisolas.recipehost.mod.snapshot.RecipeSource;
import org.sagisolas.recipehost.mod.snapshot.RenderThreadScheduler;
import org.sagisolas.recipehost.mod.snapshot.item.Ids;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.Recipe;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every recipe EMI knows, vanilla and JEI-only ones included (ADR 0008), each with the
 * layout captured from its widgets (ADR 0015). Only constructed when EMI is loaded, so
 * EMI's classes are never reached without it.
 */
public final class EmiRecipeSource implements RecipeSource {

    private static final Logger LOG = LogUtils.getLogger();
    /**
     * Categories that aren't recipes: {@code emi:tag} lists a tag's members, which the
     * Snapshot already expands into every Slot, and {@code emi:fuel} lists burn times.
     */
    private static final Set<ResourceLocation> SKIPPED_CATEGORIES =
            Set.of(ResourceLocation.fromNamespaceAndPath("emi", "tag"),
                    ResourceLocation.fromNamespaceAndPath("emi", "fuel"));
    /** JEI's tag listings, one category per registry ({@code minecraft:tag_recipes/item}, ...). */
    private static final String JEI_TAG_CATEGORY_PREFIX = "tag_recipes/";

    private final RenderThreadScheduler renderThreadScheduler;
    /** Categories already logged, so each failure is logged once per category. */
    private final Set<ResourceLocation> failedCaptures = new HashSet<>();
    private final Set<ResourceLocation> failedReads = new HashSet<>();

    public EmiRecipeSource(RenderThreadScheduler renderThreadScheduler) {
        this.renderThreadScheduler = renderThreadScheduler;
    }

    @Override
    public Recipes collect() {
        List<EmiRecipe> all = EmiApi.getRecipeManager().getRecipes().stream()
                .filter(recipe -> !isSkippedCategory(recipe.getCategory().getId()))
                .toList();
        List<Optional<CapturingWidgetHolder.Capture>> captures = captureAll(all);

        List<Recipe> recipes = new ArrayList<>();
        List<LayoutJob> layouts = new ArrayList<>();
        Set<String> namespaces = new HashSet<>();
        // A Snapshot names each recipe once: the app keys recipes by id (ADR 0011).
        Set<String> ids = new HashSet<>();
        Set<ResourceLocation> duplicateCategories = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            EmiRecipe recipe = all.get(i);
            // A recipe with no layout ships no layout_stacks either.
            Optional<CapturingWidgetHolder.Capture> capture = captures.get(i).filter(c -> c.width() > 0 && c.height() > 0);
            ResourceLocation categoryId = recipe.getCategory().getId();
            ResourceLocation id;
            Recipe wire;
            try {
                id = recipe.getId();
                wire = EmiRecipeMapper.map(id == null ? null : id.toString(), categoryId.toString(),
                        inputsOf(recipe), stacksOf(recipe.getOutputs()), layoutStacksOf(capture));
            } catch (RuntimeException e) {
                // A mod's broken recipe costs only that recipe, not the Snapshot.
                if (failedReads.add(categoryId)) {
                    LOG.warn("[recipehost] skipping EMI recipes of {} that fail to read", categoryId, e);
                }
                continue;
            }
            // Two recipes can share an id: a mod registered both under one, or two recipes without
            // one have the same body once fluids are left out. The first is kept.
            if (!ids.add(wire.getId())) {
                if (duplicateCategories.add(categoryId)) {
                    LOG.warn("[recipehost] EMI recipes of {} repeat the id {}; only the first is sent (logged once per category)",
                            categoryId, wire.getId());
                }
                continue;
            }
            recipes.add(wire);
            // A recipe without an id gets one in its category's namespace.
            namespaces.add((id != null ? id : categoryId).getNamespace());
            capture.map(c -> new EmiLayoutDrawer(c).job(categoryId.toString(), wire.getId())).ifPresent(layouts::add);
        }
        return new Recipes(recipes, layouts, namespaces);
    }

    static boolean isSkippedCategory(ResourceLocation category) {
        return SKIPPED_CATEGORIES.contains(category)
                || category.getNamespace().equals("minecraft") && category.getPath().startsWith(JEI_TAG_CATEGORY_PREFIX);
    }

    /**
     * Captures every recipe's widgets on the render thread, where mods expect
     * {@code addWidgets} to run, a budgeted slice at a time.
     */
    private List<Optional<CapturingWidgetHolder.Capture>> captureAll(List<EmiRecipe> all) {
        List<Optional<CapturingWidgetHolder.Capture>> captures = new ArrayList<>(all.size());
        while (captures.size() < all.size()) {
            int from = captures.size();
            captures.addAll(renderThreadScheduler.call(deadline -> {
                List<Optional<CapturingWidgetHolder.Capture>> slice = new ArrayList<>();
                // At least one per slice, so the loop always makes progress.
                do {
                    slice.add(capture(all.get(from + slice.size())));
                } while (from + slice.size() < all.size() && System.nanoTime() < deadline);
                return slice;
            }));
        }
        return captures;
    }

    /** The recipe's widgets, or empty if its {@code addWidgets} throws: it then ships without a layout. */
    private Optional<CapturingWidgetHolder.Capture> capture(EmiRecipe recipe) {
        try {
            CapturingWidgetHolder holder = new CapturingWidgetHolder(
                    recipe.getDisplayWidth(), recipe.getDisplayHeight(), recipe.getCatalysts());
            recipe.addWidgets(holder);
            return Optional.of(holder.capture());
        } catch (RuntimeException | LinkageError e) {
            // LinkageError: a mod's widgets compiled against an API that isn't there.
            ResourceLocation category = recipe.getCategory().getId();
            if (failedCaptures.add(category)) {
                LOG.warn("[recipehost] EMI recipes of {} fail to add their widgets; they ship without a layout", category, e);
            }
            return Optional.empty();
        }
    }

    private static List<List<EmiRecipeMapper.Stack>> inputsOf(EmiRecipe recipe) {
        List<List<EmiRecipeMapper.Stack>> inputs = new ArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            inputs.add(stacksOf(ingredient));
        }
        return inputs;
    }

    private static List<List<EmiRecipeMapper.Stack>> layoutStacksOf(Optional<CapturingWidgetHolder.Capture> capture) {
        return capture.map(c -> c.slots().stream().map(slot -> stacksOf(slot.stack())).toList()).orElse(List.of());
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
