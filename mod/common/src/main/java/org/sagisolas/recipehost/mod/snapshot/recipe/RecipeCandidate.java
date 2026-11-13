/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.recipe;

import java.util.Optional;
import org.sagisolas.recipehost.protocol.Recipe;

/**
 * One recipe as a recipe source (vanilla dispatch, EMI) finds it, not yet read. {@link RecipeAssembly}
 * decides whether it ships and owns every rule a Snapshot's recipes follow; a candidate only reads.
 * Every method may throw: the assembly isolates the failure to this recipe.
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

    /**
     * The name of the recipe's type in the game's language (ADR 0004), or empty when it has none.
     * Runs on the sender thread, for each shipping recipe of the type until one gives a name.
     */
    default Optional<String> typeName() {
        return Optional.empty();
    }
}
