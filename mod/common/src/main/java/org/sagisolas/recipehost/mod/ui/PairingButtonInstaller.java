package org.sagisolas.recipehost.mod.ui;

import com.mojang.logging.LogUtils;
import io.nayuki.qrcodegen.QrCode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.sagisolas.recipehost.mod.pairing.PairingController;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Adds a vanilla-styled "QR" {@link Button} to the survival and creative inventory screens,
 * in the bottom-left corner, matching EMI's buttons there (see {@link PairingButtonPlacement}).
 */
public final class PairingButtonInstaller {

    private static final Logger LOG = LogUtils.getLogger();
    private static final Component LABEL = Component.literal("QR");
    private static final Component TOOLTIP = Component.literal("Pair with RecipeHost app");

    private final PairingController pairing;
    private final boolean emiLoaded;

    public PairingButtonInstaller(PairingController pairing, boolean emiLoaded) {
        this.pairing = pairing;
        this.emiLoaded = emiLoaded;
    }

    public void install(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;
        PairingButtonPlacement at = PairingButtonPlacement.of(screen.height, emiLoaded);
        addWidget.accept(Button.builder(LABEL, b -> openPairing(screen))
                .bounds(at.x(), at.y(), PairingButtonPlacement.SIZE, PairingButtonPlacement.SIZE)
                .tooltip(Tooltip.create(TOOLTIP))
                .createNarration(defaultNarration -> TOOLTIP.copy())
                .build());
    }

    private void openPairing(Screen parent) {
        QrCode qr;
        try {
            String uri = pairing.open();
            LOG.info("[recipehost] pairing open: {}", uri);
            // Throws DataTooLongException if the host list outgrows a QR code.
            qr = QrCode.encodeText(uri, QrCode.Ecc.MEDIUM);
        } catch (IOException | RuntimeException e) {
            LOG.error("[recipehost] failed to open pairing", e);
            pairing.screenClosed(); // releases a listener that may already be bound
            return;
        }
        Minecraft.getInstance().setScreen(new PairingScreen(parent, qr, pairing::screenClosed));
    }
}
