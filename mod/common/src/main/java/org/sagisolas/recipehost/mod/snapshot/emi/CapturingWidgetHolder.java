/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import org.sagisolas.recipehost.protocol.RecipeSlot;

/**
 * The {@link WidgetHolder} passed to {@code EmiRecipe.addWidgets}: records the widgets of
 * one recipe in order, and turns each {@link SlotWidget} (Jemi slots and tanks included)
 * into a `Recipe slot` with the ingredient it shows (ADR 0015). Slots are read only in
 * {@link #capture()}: EMI's builders configure a slot after adding it
 * ({@code addSlot(...).large(true).recipeContext(this)}).
 */
final class CapturingWidgetHolder implements WidgetHolder {

    /** A `Recipe slot`, and the ingredient its widget shows. */
    record CapturedSlot(RecipeSlot slot, EmiIngredient stack) {}

    /** A recipe's display size, its widgets in the order they were added, and its slots in the same order. */
    record Capture(int width, int height, List<Widget> widgets, List<CapturedSlot> slots) {
        Capture {
            widgets = List.copyOf(widgets);
            slots = List.copyOf(slots);
        }
    }

    private final int width;
    private final int height;
    private final List<EmiIngredient> catalysts;
    private final List<Widget> widgets = new ArrayList<>();

    /**
     * @param width     the recipe's display width
     * @param height    the recipe's display height
     * @param catalysts the recipe's catalysts, whose slots are {@code CATALYST}
     */
    CapturingWidgetHolder(int width, int height, List<EmiIngredient> catalysts) {
        this.width = width;
        this.height = height;
        this.catalysts = List.copyOf(catalysts);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public <T extends Widget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    /**
     * The recipe as {@code addWidgets} left it. Call once it returns, and once: each slot's
     * {@code getStack()} is sampled here, and a generated slot would pick another stack.
     */
    Capture capture() {
        List<CapturedSlot> slots = new ArrayList<>();
        for (Widget widget : widgets) {
            if (widget instanceof SlotWidget slot) {
                slots.add(new CapturedSlot(recipeSlot(slot.getBounds(), roleOf(slot)), slot.getStack()));
            }
        }
        return new Capture(width, height, widgets, slots);
    }

    /**
     * EMI marks an output slot with its recipe. A catalyst slot's flag is protected, so
     * it is told by showing one of the recipe's catalysts instead.
     */
    private RecipeSlot.Role roleOf(SlotWidget slot) {
        if (slot.getRecipe() != null) return RecipeSlot.Role.OUTPUT;
        return catalysts.contains(slot.getStack()) ? RecipeSlot.Role.CATALYST : RecipeSlot.Role.INPUT;
    }

    private static RecipeSlot recipeSlot(Bounds bounds, RecipeSlot.Role role) {
        return RecipeSlot.newBuilder()
                .setX(bounds.x())
                .setY(bounds.y())
                .setWidth(bounds.width())
                .setHeight(bounds.height())
                .setRole(role)
                .build();
    }
}
