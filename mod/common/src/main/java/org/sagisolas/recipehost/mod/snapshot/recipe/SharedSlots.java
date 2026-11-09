package org.sagisolas.recipehost.mod.snapshot.recipe;

import org.sagisolas.recipehost.protocol.Recipe;
import org.sagisolas.recipehost.protocol.RecipeBatch;
import org.sagisolas.recipehost.protocol.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link RecipeBatch} that sends each repeated Slot once (ADR 0016): a Slot with two or
 * more alternatives that appears more than once among the batch's {@code inputs} and
 * {@code layout_stacks} goes into {@code shared_slots}, and each occurrence refers to it.
 */
public final class SharedSlots {

    private SharedSlots() {}

    public static RecipeBatch batchOf(List<Recipe> recipes) {
        Map<Slot, Integer> occurrences = new HashMap<>();
        for (Recipe recipe : recipes) {
            for (Slot slot : recipe.getInputsList()) count(occurrences, slot);
            for (Slot slot : recipe.getLayoutStacksList()) count(occurrences, slot);
        }
        Map<Slot, Integer> shared = new LinkedHashMap<>();
        List<Recipe> out = new ArrayList<>(recipes.size());
        for (Recipe recipe : recipes) {
            Recipe.Builder builder = recipe.toBuilder().clearInputs().clearLayoutStacks();
            for (Slot slot : recipe.getInputsList()) builder.addInputs(refer(slot, occurrences, shared));
            for (Slot slot : recipe.getLayoutStacksList()) builder.addLayoutStacks(refer(slot, occurrences, shared));
            out.add(builder.build());
        }
        return RecipeBatch.newBuilder().addAllRecipes(out).addAllSharedSlots(shared.keySet()).build();
    }

    private static void count(Map<Slot, Integer> occurrences, Slot slot) {
        if (slot.getStacksCount() >= 2) occurrences.merge(slot, 1, Integer::sum);
    }

    private static Slot refer(Slot slot, Map<Slot, Integer> occurrences, Map<Slot, Integer> shared) {
        if (occurrences.getOrDefault(slot, 0) < 2) return slot;
        int index = shared.computeIfAbsent(slot, s -> shared.size() + 1);
        return Slot.newBuilder().setShared(index).build();
    }
}
