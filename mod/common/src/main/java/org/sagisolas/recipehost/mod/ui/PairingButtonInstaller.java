package org.sagisolas.recipehost.mod.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.sagisolas.recipehost.mod.pairing.PairingController;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Adds a {@link PairingButton} to each survival inventory screen, in the first free
 * slot right of vanilla's recipe-book button. The slot is chosen once, at init, from
 * the screen's existing widgets.
 */
public final class PairingButtonInstaller {

    private static final Logger LOG = LogUtils.getLogger();
    private static final int STEP = 22;
    private static final int MAX_SLOTS = 16;

    private final PairingController pairing;

    public PairingButtonInstaller(PairingController pairing) {
        this.pairing = pairing;
    }

    public void install(InventoryScreen screen, Consumer<AbstractWidget> addWidget) {
        // Vanilla adds the recipe-book button first in InventoryScreen#init.
        AbstractWidget anchor = screen.children().stream()
                .filter(ImageButton.class::isInstance)
                .map(AbstractWidget.class::cast)
                .findFirst()
                .orElse(null);
        if (anchor == null) {
            LOG.warn("[recipehost] no recipe-book button on inventory screen; pairing button not added");
            return;
        }

        for (int slot = 1; slot <= MAX_SLOTS; slot++) {
            int x = anchor.getX() + STEP * slot;
            int y = anchor.getY();
            boolean taken = screen.children().stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .anyMatch(w -> overlaps(w, x, y));
            if (!taken) {
                addWidget.accept(new PairingButton(anchor, STEP * slot, button -> openPairing()));
                return;
            }
        }
        LOG.warn("[recipehost] recipe-book row full; pairing button not added");
    }

    private void openPairing() {
        // Until the QR screen lands (issue 04): bind, log the URI, and start the grace timer.
        try {
            LOG.info("[recipehost] pairing open: {}", pairing.open());
            pairing.screenClosed();
        } catch (IOException e) {
            LOG.error("[recipehost] failed to open pairing", e);
        }
    }

    private static boolean overlaps(AbstractWidget w, int x, int y) {
        return w.getX() < x + PairingButton.WIDTH && x < w.getX() + w.getWidth()
                && w.getY() < y + PairingButton.HEIGHT && y < w.getY() + w.getHeight();
    }
}
