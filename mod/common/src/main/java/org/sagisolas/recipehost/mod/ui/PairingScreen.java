package org.sagisolas.recipehost.mod.ui;

import io.nayuki.qrcodegen.QrCode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The QR screen: shows the pairing payload as a QR code with a scan hint below it, and a red
 * warning under the hint when custom recipe types will ship without inputs (no EMI, ADR 0008).
 * Drawn with plain {@link GuiGraphics#fill} calls, so it needs no texture.
 * Leaving the screen, by any path, runs {@code onRemoved} (which starts the grace timer).
 */
public final class PairingScreen extends Screen {

    private static final Component TITLE = Component.literal("RecipeHost pairing");
    private static final Component SCAN_HINT = Component.literal("Scan this code with \"Recipehost\" app");
    private static final Component CUSTOM_RECIPES_WARNING =
            Component.literal("Custom recipe types are not supported without EMI installed");
    private static final int QUIET_ZONE = 4;
    private static final int DARK = 0xFF000000;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int TEXT = 0xFFFFFF;
    private static final int WARNING = 0xFF5555;
    private static final int GAP = 8;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final QrCode qr;
    private final Runnable onRemoved;
    private final boolean warnCustomRecipes;

    private int modulePx;
    private int qrLeft;
    private int qrTop;
    private int qrSide;
    private MultiLineLabel warning = MultiLineLabel.EMPTY;

    public PairingScreen(Screen parent, QrCode qr, boolean warnCustomRecipes, Runnable onRemoved) {
        super(TITLE);
        this.parent = parent;
        this.qr = qr;
        this.warnCustomRecipes = warnCustomRecipes;
        this.onRemoved = onRemoved;
    }

    @Override
    protected void init() {
        int modules = qr.size + 2 * QUIET_ZONE;
        int target = (int) (Math.min(width, height) * 0.6);
        modulePx = Math.max(1, target / modules);
        qrSide = modules * modulePx;

        warning = warnCustomRecipes
                ? MultiLineLabel.create(font, CUSTOM_RECIPES_WARNING, width - 2 * GAP)
                : MultiLineLabel.EMPTY;
        int contentHeight = qrSide + GAP + font.lineHeight + GAP + warningHeight() + BUTTON_HEIGHT;
        qrLeft = (width - qrSide) / 2;
        qrTop = Math.max(GAP, (height - contentHeight) / 2);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds((width - BUTTON_WIDTH) / 2, buttonTop(), BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private int captionTop() {
        return qrTop + qrSide + GAP;
    }

    private int warningTop() {
        return captionTop() + font.lineHeight + GAP;
    }

    /** The warning's lines plus the gap below them, or nothing without a warning. */
    private int warningHeight() {
        int lines = warning.getLineCount();
        return lines == 0 ? 0 : lines * font.lineHeight + GAP;
    }

    private int buttonTop() {
        return warningTop() + warningHeight();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(qrLeft, qrTop, qrLeft + qrSide, qrTop + qrSide, LIGHT);
        int origin = QUIET_ZONE * modulePx;
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    int px = qrLeft + origin + x * modulePx;
                    int py = qrTop + origin + y * modulePx;
                    graphics.fill(px, py, px + modulePx, py + modulePx, DARK);
                }
            }
        }

        graphics.drawCenteredString(font, SCAN_HINT, width / 2, captionTop(), TEXT);
        warning.renderCentered(graphics, width / 2, warningTop(), font.lineHeight, WARNING);
    }

    @Override
    public boolean isPauseScreen() {
        // Keep singleplayer ticking: icons render on the render thread while the phone pulls the snapshot.
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        onRemoved.run();
    }
}
