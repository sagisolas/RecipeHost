package org.sagisolas.recipehost.mod.snapshot.recipe;

import java.util.List;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.Slot;

/**
 * A recipe's `Recipe layout` and what each of its `Recipe slot`s shows: {@code stacks.get(i)} fills
 * {@code job.slots().get(i)}. Built together, so the two always line up.
 */
public record LaidOut(LayoutJob job, List<Slot> stacks) {

    public LaidOut {
        stacks = List.copyOf(stacks);
        if (stacks.size() != job.slots().size()) {
            throw new IllegalArgumentException(
                    "a layout with " + job.slots().size() + " Recipe slots got " + stacks.size() + " stacks");
        }
    }
}
