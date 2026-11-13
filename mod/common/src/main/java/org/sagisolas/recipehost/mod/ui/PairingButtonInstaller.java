/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.ui;

import com.mojang.logging.LogUtils;
import io.nayuki.qrcodegen.QrCode;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.sagisolas.recipehost.mod.pairing.PairingController;
import org.slf4j.Logger;

/**
 * Adds the {@link PairingButton} to the survival and creative inventory screens, next to the
 * recipe viewer's bottom-left buttons (see {@link PairingButtonPlacement}).
 */
public final class PairingButtonInstaller {

    private static final Logger LOG = LogUtils.getLogger();
    private static final Component TOOLTIP = Component.literal("Pair with RecipeHost app");

    private final PairingController pairing;
    private final RecipeViewer viewer;
    private final BooleanSupplier customRecipesUnsupported;

    /**
     * @param customRecipesUnsupported asked each time the QR screen opens: whether to warn that
     *     custom recipe types will ship without inputs
     */
    public PairingButtonInstaller(
            PairingController pairing, RecipeViewer viewer, BooleanSupplier customRecipesUnsupported) {
        this.pairing = pairing;
        this.viewer = viewer;
        this.customRecipesUnsupported = customRecipesUnsupported;
    }

    public void install(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;
        PairingButtonPlacement at = PairingButtonPlacement.of(screen.height, viewer);
        addWidget.accept(new PairingButton(at, viewer, TOOLTIP, () -> openPairing(screen)));
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
        Minecraft.getInstance()
                .setScreen(
                        new PairingScreen(parent, qr, customRecipesUnsupported.getAsBoolean(), pairing::screenClosed));
    }
}
