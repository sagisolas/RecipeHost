package org.sagisolas.recipehost.mod;

import net.minecraft.client.multiplayer.ClientLevel;
import org.sagisolas.recipehost.mod.pairing.ExecutorScheduler;
import org.sagisolas.recipehost.mod.pairing.HostAddresses;
import org.sagisolas.recipehost.mod.pairing.MinecraftListenerFactory;
import org.sagisolas.recipehost.mod.pairing.PairingController;
import org.sagisolas.recipehost.mod.pairing.SecureTokens;
import org.sagisolas.recipehost.mod.snapshot.RecipeSource;
import org.sagisolas.recipehost.mod.snapshot.RenderThreadScheduler;
import org.sagisolas.recipehost.mod.snapshot.emi.EmiRecipeSource;
import org.sagisolas.recipehost.mod.snapshot.vanilla.VanillaRecipeSource;
import org.sagisolas.recipehost.mod.ui.PairingButtonInstaller;

import java.util.function.Function;

/** Composition root: called once by each loader's entrypoint; creates and wires the mod's objects. */
public final class RecipeHost {

    public static final String MOD_ID = "recipehost";
    private static final String EMI_MOD_ID = "emi";

    private RecipeHost() {}

    public static void init() {
        boolean emiLoaded = Platform.listMods().stream().anyMatch(mod -> mod.id().equals(EMI_MOD_ID));
        RenderThreadScheduler renderThreadScheduler = new RenderThreadScheduler();
        PairingController pairing = new PairingController(
                new MinecraftListenerFactory(recipeSources(emiLoaded), renderThreadScheduler),
                new ExecutorScheduler("recipehost-pairing-timer"),
                new SecureTokens(),
                HostAddresses::all);
        PairingButtonInstaller buttons = new PairingButtonInstaller(pairing, emiLoaded);

        Platform.onScreenInit(buttons::install);
        Platform.onWorldLeave(pairing::worldLeft);
    }

    /** EMI when it's loaded (ADR 0008); its classes are only reached in that branch. */
    private static Function<ClientLevel, RecipeSource> recipeSources(boolean emiLoaded) {
        return emiLoaded ? level -> new EmiRecipeSource() : VanillaRecipeSource::new;
    }
}
