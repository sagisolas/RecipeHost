package org.sagisolas.recipehost.mod.snapshot.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * A stand-in {@link EmiIngredient} that holds no stacks: EMI's own stacks need its
 * runtime, which tests don't have. Ingredients are compared by identity.
 */
final class FakeIngredient implements EmiIngredient {

    private final String name;

    private FakeIngredient(String name) {
        this.name = name;
    }

    static FakeIngredient of(String name) {
        return new FakeIngredient(name);
    }

    @Override
    public List<EmiStack> getEmiStacks() {
        return List.of();
    }

    @Override
    public EmiIngredient copy() {
        return this;
    }

    @Override
    public long getAmount() {
        return 1;
    }

    @Override
    public EmiIngredient setAmount(long amount) {
        return this;
    }

    @Override
    public float getChance() {
        return 1;
    }

    @Override
    public EmiIngredient setChance(float chance) {
        return this;
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta, int flags) {}

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        return List.of();
    }

    @Override
    public String toString() {
        return name;
    }
}
