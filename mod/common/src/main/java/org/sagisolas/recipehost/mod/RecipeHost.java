package org.sagisolas.recipehost.mod;

import org.sagisolas.recipehost.mod.pairing.ExecutorScheduler;
import org.sagisolas.recipehost.mod.pairing.HostAddresses;
import org.sagisolas.recipehost.mod.pairing.MinecraftListenerFactory;
import org.sagisolas.recipehost.mod.pairing.PairingController;
import org.sagisolas.recipehost.mod.pairing.SecureTokens;
import org.sagisolas.recipehost.mod.ui.PairingButtonInstaller;

/** Composition root: called once by each loader's entrypoint; creates and wires the mod's objects. */
public final class RecipeHost {

    public static final String MOD_ID = "recipehost";

    private RecipeHost() {}

    public static void init() {
        PairingController pairing = new PairingController(
                new MinecraftListenerFactory(),
                new ExecutorScheduler("recipehost-pairing-timer"),
                new SecureTokens(),
                HostAddresses::all);
        PairingButtonInstaller buttons = new PairingButtonInstaller(pairing);

        Platform.onInventoryInit(buttons::install);
        Platform.onWorldLeave(pairing::worldLeft);
    }
}
