package org.sagisolas.recipehost.mod.ui;

import com.mojang.logging.LogUtils;
import io.nayuki.qrcodegen.QrCode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.sagisolas.recipehost.mod.RecipeHost;
import org.sagisolas.recipehost.mod.pairing.PairingController;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Adds a pairing {@link ImageButton} (icon only, no button background) to each
 * survival inventory screen, in the
 * top-left corner of the whole screen. If another widget is already there, the button
 * steps right to the first free slot. The slot is chosen once, at init.
 */
public final class PairingButtonInstaller {

    private static final Logger LOG = LogUtils.getLogger();
    private static final int SIZE = 20;
    private static final int MARGIN = 4;
    private static final int STEP = SIZE + 2;
    private static final int MAX_SLOTS = 16;
    /** {@code assets/recipehost/textures/gui/sprites/pairing_button.png} in the GUI atlas. */
    private static final ResourceLocation SPRITE = ResourceLocation.fromNamespaceAndPath(RecipeHost.MOD_ID, "pairing_button");
    private static final Component LABEL = Component.literal("Pair with RecipeHost app");

    private final PairingController pairing;

    public PairingButtonInstaller(PairingController pairing) {
        this.pairing = pairing;
    }

    public void install(InventoryScreen screen, Consumer<AbstractWidget> addWidget) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            int x = MARGIN + STEP * slot;
            boolean taken = screen.children().stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .anyMatch(w -> overlaps(w, x, MARGIN));
            if (!taken) {
                addWidget.accept(createButton(screen, x, MARGIN));
                return;
            }
        }
        LOG.warn("[recipehost] top-left row full; pairing button not added");
    }

    private ImageButton createButton(InventoryScreen screen, int x, int y) {
        // ImageButton draws only its sprite. LABEL is used for narration and the tooltip.
        ImageButton button = new ImageButton(x, y, SIZE, SIZE, new WidgetSprites(SPRITE, SPRITE),
                b -> openPairing(screen), LABEL);
        button.setTooltip(Tooltip.create(LABEL));
        return button;
    }

    private void openPairing(InventoryScreen parent) {
        QrCode qr;
        try {
            String uri = pairing.open();
            LOG.info("[recipehost] pairing open: {}", uri);
            // Throws DataTooLongException if the unfiltered host list outgrows a QR code.
            qr = QrCode.encodeText(uri, QrCode.Ecc.MEDIUM);
        } catch (IOException | RuntimeException e) {
            LOG.error("[recipehost] failed to open pairing", e);
            pairing.screenClosed(); // releases a listener that may already be bound
            return;
        }
        Minecraft.getInstance().setScreen(new PairingScreen(parent, qr, pairing::screenClosed));
    }

    private static boolean overlaps(AbstractWidget w, int x, int y) {
        return w.getX() < x + SIZE && x < w.getX() + w.getWidth()
                && w.getY() < y + SIZE && y < w.getY() + w.getHeight();
    }
}
