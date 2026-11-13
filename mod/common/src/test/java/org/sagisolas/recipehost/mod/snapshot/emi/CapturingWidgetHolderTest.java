/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.emi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.junit.Test;
import org.sagisolas.recipehost.protocol.RecipeSlot;

public class CapturingWidgetHolderTest {

    private static Widget texture(int x, int y) {
        return new Widget() {
            @Override
            public Bounds getBounds() {
                return new Bounds(x, y, 10, 10);
            }

            @Override
            public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {}
        };
    }

    private static RecipeSlot slot(int x, int y, int size, RecipeSlot.Role role) {
        return RecipeSlot.newBuilder()
                .setX(x)
                .setY(y)
                .setWidth(size)
                .setHeight(size)
                .setRole(role)
                .build();
    }

    @Test
    public void slotWidgetsBecomeRecipeSlotsInOrderAndEveryWidgetIsKeptInOrder() {
        CapturingWidgetHolder holder = new CapturingWidgetHolder(134, 60, List.of());
        Widget background = texture(0, 0);
        SlotWidget input = new SlotWidget(FakeIngredient.of("minecraft:stick"), 1, 2);
        Widget arrow = texture(40, 2);
        SlotWidget large = new SlotWidget(FakeIngredient.of("minecraft:torch"), 90, 0).large(true);

        holder.add(background);
        holder.add(input);
        holder.add(arrow);
        holder.add(large);
        CapturingWidgetHolder.Capture capture = holder.capture();

        assertEquals(List.of(background, input, arrow, large), capture.widgets());
        assertEquals(
                List.of(slot(1, 2, 18, RecipeSlot.Role.INPUT), slot(90, 0, 26, RecipeSlot.Role.INPUT)),
                capture.slots().stream()
                        .map(CapturingWidgetHolder.CapturedSlot::slot)
                        .toList());
        assertSame(input.getStack(), capture.slots().get(0).stack());
        assertEquals(134, holder.getWidth());
        assertEquals(60, holder.getHeight());
    }

    @Test
    public void aSlotWithARecipeContextIsAnOutputAndOneShowingACatalystIsACatalyst() {
        FakeIngredient fuel = FakeIngredient.of("minecraft:coal");
        CapturingWidgetHolder holder = new CapturingWidgetHolder(134, 60, List.of(fuel));

        holder.add(new SlotWidget(FakeIngredient.of("minecraft:iron_ore"), 0, 0));
        holder.add(new SlotWidget(fuel, 0, 36).catalyst(true));
        holder.add(new SlotWidget(FakeIngredient.of("minecraft:iron_ingot"), 60, 18).recipeContext(new NoRecipe()));

        assertEquals(
                List.of(RecipeSlot.Role.INPUT, RecipeSlot.Role.CATALYST, RecipeSlot.Role.OUTPUT),
                holder.capture().slots().stream()
                        .map(captured -> captured.slot().getRole())
                        .toList());
    }

    @Test
    public void aSlotIsReadAsItIsOnceAddWidgetsIsDoneNotAsItWasWhenAdded() {
        // EMI's builders configure a slot after adding it: addSlot(output, 56, 0).large(true).recipeContext(this).
        CapturingWidgetHolder holder = new CapturingWidgetHolder(82, 38, List.of());

        holder.add(new SlotWidget(FakeIngredient.of("minecraft:cooked_chicken"), 56, 0))
                .large(true)
                .recipeContext(new NoRecipe());

        assertEquals(
                List.of(slot(56, 0, 26, RecipeSlot.Role.OUTPUT)),
                holder.capture().slots().stream()
                        .map(CapturingWidgetHolder.CapturedSlot::slot)
                        .toList());
    }

    /** Only marks a slot as showing a recipe's output; never asked for anything. */
    private static final class NoRecipe implements EmiRecipe {
        @Override
        public EmiRecipeCategory getCategory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceLocation getId() {
            return null;
        }

        @Override
        public List<EmiIngredient> getInputs() {
            return List.of();
        }

        @Override
        public List<EmiStack> getOutputs() {
            return List.of();
        }

        @Override
        public int getDisplayWidth() {
            return 0;
        }

        @Override
        public int getDisplayHeight() {
            return 0;
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {}
    }
}
