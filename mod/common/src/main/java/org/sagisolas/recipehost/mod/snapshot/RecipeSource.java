package org.sagisolas.recipehost.mod.snapshot;

import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.Recipe;

import java.util.List;
import java.util.Set;

/** Where a Snapshot's {@code Recipe}s come from: the vanilla dispatch, or EMI when it's loaded (ADR 0008). */
public interface RecipeSource {

    /**
     * @param layouts    the jobs that render the layouts of {@code recipes}, for those that have one
     * @param namespaces the namespaces of the recipe ids, which own the recipes (ADR 0011)
     */
    record Recipes(List<Recipe> recipes, List<LayoutJob> layouts, Set<String> namespaces) {
        public Recipes {
            recipes = List.copyOf(recipes);
            layouts = List.copyOf(layouts);
            namespaces = Set.copyOf(namespaces);
        }
    }

    /** Reads every recipe the client knows. Runs on the sender thread, on the first handshake. */
    Recipes collect();
}
