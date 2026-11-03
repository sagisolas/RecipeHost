package org.sagisolas.recipehost.mod.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * "QR" button in the inventory's recipe-book row. Tracks {@code anchor} (vanilla's
 * recipe-book button) at a fixed offset, so it follows the recipe-book toggle.
 * Created and placed by {@link PairingButtonInstaller}.
 */
public final class PairingButton extends Button {

    static final int WIDTH = 20;
    static final int HEIGHT = 18;

    private final AbstractWidget anchor;
    private final int offsetX;

    PairingButton(AbstractWidget anchor, int offsetX, OnPress onPress) {
        super(anchor.getX() + offsetX, anchor.getY(), WIDTH, HEIGHT,
                Component.literal("QR"), onPress, DEFAULT_NARRATION);
        this.anchor = anchor;
        this.offsetX = offsetX;
        setTooltip(Tooltip.create(Component.literal("Pair with RecipeHost app")));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        setPosition(anchor.getX() + offsetX, anchor.getY());
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
