package org.sagisolas.recipehost.mod.snapshot;

import java.util.List;
import org.sagisolas.recipehost.mod.snapshot.recipe.RecipeCandidate;

/**
 * Where a Snapshot's {@code Recipe}s come from: the vanilla dispatch, or EMI when it's loaded
 * (ADR 0008). A source only finds and reads recipes; {@link org.sagisolas.recipehost.mod.snapshot.recipe.RecipeAssembly}
 * decides which of them ship.
 */
public interface RecipeSource {

    /** Every recipe the client knows, unread. Runs on the sender thread, on the first handshake. */
    List<RecipeCandidate> candidates();
}
