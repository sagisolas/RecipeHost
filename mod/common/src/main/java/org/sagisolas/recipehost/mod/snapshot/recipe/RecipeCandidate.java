package org.sagisolas.recipehost.mod.snapshot.recipe;

import org.sagisolas.recipehost.protocol.Recipe;

import java.util.Optional;

/**
 * One recipe as a recipe source (vanilla dispatch, EMI) finds it, not yet read. {@link RecipeAssembly}
 * decides whether it ships and owns every rule a Snapshot's recipes follow; a candidate only reads.
 * Both methods may throw: the assembly isolates the failure to this recipe.
 */
public interface RecipeCandidate {

    /** The recipe type (or EMI category) id: it decides skipping and names the recipe in logs. */
    String typeId();

    /** The wire recipe with its id set and no {@code layout_stacks}. Runs on the sender thread. */
    Recipe read();

    /**
     * The recipe's `Recipe layout` and what each of its `Recipe slot`s shows, or empty when it has
     * none. Gets what {@link #read()} returned. Runs on the render thread.
     */
    Optional<LaidOut> layOut(Recipe read);
}
