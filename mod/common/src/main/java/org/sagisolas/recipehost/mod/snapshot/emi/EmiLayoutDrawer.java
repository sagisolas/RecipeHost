/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.emi;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.AnimatedTextureWidget;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.FillingArrowWidget;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.sagisolas.recipehost.mod.snapshot.layout.LayoutJob;
import org.sagisolas.recipehost.protocol.RecipeSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws a captured EMI recipe as its `Recipe layout` (ADR 0015): on the GUI's light panel
 * colour with a {@link #PADDING} border, as EMI's recipe screen shows it, every widget as EMI
 * draws it, except:
 * <ul>
 *   <li>slots show only their background: the app draws the Items;</li>
 *   <li>filling arrows show only their empty frame;</li>
 *   <li>other animated textures (a furnace's flame) aren't drawn, so the static texture EMI puts
 *       under them shows. They animate by the wall clock, so each capture would catch them at a
 *       different frame, and equal layouts would differ.</li>
 * </ul>
 * Slots are judged by the stack captured with them, not a fresh {@code getStack()}: a generated
 * slot would pick another one.
 *
 * <p>The widgets aren't kept from the capture made while the recipe was read: a Snapshot holds
 * every job until its layouts are drawn, and a widget tree per recipe ran the game out of memory
 * on a large modpack (ADR 0015). The drawer keeps the recipe, which EMI holds anyway, and runs
 * {@code addWidgets} again when it draws.
 */
final class EmiLayoutDrawer implements LayoutJob.Drawer {

    /** EMI's widget sheet; a filling arrow's empty frame sits at (44, 0). */
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("emi", "textures/gui/widgets.png");

    private static final int ARROW_U = 44;
    private static final int ARROW_V = 0;
    private static final float NO_DELTA = 0f;
    /** Vanilla's GUI panel grey, behind every container screen. */
    private static final int PANEL = 0xFFC6C6C6;
    /** Panel around the recipe's own display area, in GUI px on each side. */
    private static final int PADDING = 4;

    private static final Logger LOG = LoggerFactory.getLogger(EmiLayoutDrawer.class);

    private final EmiRecipe recipe;
    private final int width;
    private final int height;
    private final List<RecipeSlot> slots;

    /** @param capture the recipe's capture when it was read: its size and slots are what the job ships */
    EmiLayoutDrawer(EmiRecipe recipe, CapturingWidgetHolder.Capture capture) {
        this.recipe = recipe;
        this.width = capture.width();
        this.height = capture.height();
        this.slots = capture.slots().stream()
                .map(captured -> padded(captured.slot()))
                .toList();
    }

    /** The job that renders this layout for the recipe {@code recipeId} of category {@code typeId}. */
    LayoutJob job(String typeId, String recipeId) {
        return LayoutJob.hashed(typeId, width + 2 * PADDING, height + 2 * PADDING, slots, List.of(recipeId), this);
    }

    @Override
    public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width + 2 * PADDING, height + 2 * PADDING, PANEL);
        graphics.pose().pushPose();
        graphics.pose().translate(PADDING, PADDING, 0f);
        try {
            drawWidgets(recapture(), graphics, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    /** The recipe's widgets as {@code addWidgets} adds them now, on the render thread. */
    private CapturingWidgetHolder.Capture recapture() {
        CapturingWidgetHolder holder =
                new CapturingWidgetHolder(recipe.getDisplayWidth(), recipe.getDisplayHeight(), recipe.getCatalysts());
        recipe.addWidgets(holder);
        CapturingWidgetHolder.Capture capture = holder.capture();
        // Drawn anyway: only a recipe whose addWidgets isn't repeatable gets here, and its own
        // slots still decide what each slot bakes in.
        if (capture.slots().size() != slots.size()) {
            LOG.debug(
                    "[recipehost] EMI recipe {} drew {} slots, but was read with {}",
                    recipe.getId(),
                    capture.slots().size(),
                    slots.size());
        }
        return capture;
    }

    private static void drawWidgets(
            CapturingWidgetHolder.Capture capture, GuiGraphics graphics, int mouseX, int mouseY) {
        List<CapturingWidgetHolder.CapturedSlot> slotStacks = capture.slots();
        int nextSlot = 0;
        for (Widget widget : capture.widgets()) {
            if (widget instanceof FillingArrowWidget) {
                Bounds bounds = widget.getBounds();
                graphics.blit(WIDGETS, bounds.x(), bounds.y(), ARROW_U, ARROW_V, bounds.width(), bounds.height());
            } else if (widget instanceof AnimatedTextureWidget) {
                // Left out: its frame depends on the moment of capture (see the class comment).
            } else if (widget instanceof SlotWidget slot) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                slot.drawBackground(graphics, mouseX, mouseY, NO_DELTA);
                // Fluids and other non-item stacks are baked in: the app can only draw Items.
                // A generated slot's drawStack would draw a newly generated stack, so it's left out.
                boolean bake =
                        showsNonItem(slotStacks.get(nextSlot++).stack()) && !(slot instanceof GeneratedSlotWidget);
                if (bake) slot.drawStack(graphics, mouseX, mouseY, NO_DELTA);
            } else {
                // Called through Minecraft's Renderable, which Widget implements: in production
                // render is Renderable's intermediary method, and the remapper only renames the
                // call when its owner is a Minecraft class (EMI's hierarchy isn't on its classpath).
                ((Renderable) widget).render(graphics, mouseX, mouseY, NO_DELTA);
            }
        }
    }

    private static RecipeSlot padded(RecipeSlot slot) {
        return slot.toBuilder()
                .setX(slot.getX() + PADDING)
                .setY(slot.getY() + PADDING)
                .build();
    }

    private static boolean showsNonItem(EmiIngredient ingredient) {
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (!stack.isEmpty() && !(stack.getKey() instanceof Item)) return true;
        }
        return false;
    }
}
