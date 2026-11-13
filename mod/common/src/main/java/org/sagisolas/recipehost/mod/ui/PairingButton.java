/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.sagisolas.recipehost.mod.RecipeHost;

/**
 * The 20x20 pairing button: a smartphone icon on a background matching the recipe viewer's own
 * corner buttons ({@link RecipeViewer}). The label is used only for narration and the tooltip.
 */
final class PairingButton extends AbstractButton {

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(RecipeHost.MOD_ID, "pairing_phone");
    private static final int ICON_SIZE = 16;

    private final Skin skin;
    private final Runnable onPress;

    PairingButton(PairingButtonPlacement at, RecipeViewer viewer, Component label, Runnable onPress) {
        super(at.x(), at.y(), PairingButtonPlacement.SIZE, PairingButtonPlacement.SIZE, label);
        this.skin = Skin.of(viewer);
        this.onPress = onPress;
        setTooltip(Tooltip.create(label));
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        RenderSystem.enableBlend();
        graphics.blitSprite(hovered ? skin.hoveredBackground() : skin.background(), getX(), getY(), width, height);
        float shade = hovered ? 1f : skin.iconShade();
        graphics.setColor(shade, shade, shade, 1f);
        int inset = (PairingButtonPlacement.SIZE - ICON_SIZE) / 2;
        graphics.blitSprite(ICON, getX() + inset, getY() + inset, ICON_SIZE, ICON_SIZE);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /**
     * Background sprites and resting icon shade. JEI's are its own GUI sprites (nine-sliced from
     * {@code textures/gui/sprites}, so drawable without a JEI dependency) with its icon tint;
     * otherwise vanilla's button, the style EMI's corner buttons follow.
     */
    private record Skin(ResourceLocation background, ResourceLocation hoveredBackground, float iconShade) {

        static Skin of(RecipeViewer viewer) {
            return switch (viewer) {
                case JEI ->
                    new Skin(
                            ResourceLocation.fromNamespaceAndPath("jei", "button_enabled_v2"),
                            ResourceLocation.fromNamespaceAndPath("jei", "button_highlight_v2"),
                            0xE0 / 255f);
                case EMI, NONE ->
                    new Skin(
                            ResourceLocation.withDefaultNamespace("widget/button"),
                            ResourceLocation.withDefaultNamespace("widget/button_highlighted"),
                            1f);
            };
        }
    }
}
