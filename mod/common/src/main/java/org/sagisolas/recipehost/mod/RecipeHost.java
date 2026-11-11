package org.sagisolas.recipehost.mod;

import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.sagisolas.recipehost.mod.ui.RecipeViewer;

/** Composition root: called once by each loader's entrypoint; creates and wires the mod's objects. */
public final class RecipeHost {

    public static final String MOD_ID = "recipehost";

    private RecipeHost() {}

    public static void init() {
        RecipeViewer viewer = RecipeViewer.detect(Platform.listMods());
        boolean emiLoaded = viewer == RecipeViewer.EMI;
        RenderThreadScheduler renderThread = new RenderThreadScheduler();
        PairingController pairing = new PairingController(
                new MinecraftListenerFactory(recipeSources(emiLoaded), renderThread),
                new ExecutorScheduler("recipehost-pairing-timer"),
                new SecureTokens(),
                HostAddresses::all);
        PairingButtonInstaller buttons =
                new PairingButtonInstaller(pairing, viewer, customRecipesUnsupported(emiLoaded));

        Platform.onScreenInit(buttons::install);
        Platform.onWorldLeave(pairing::worldLeft);
    }

    /**
     * Without EMI, recipes of types a mod registered ship as shells (ADR 0008). JEI's types are
     * virtual, so they don't count. Read when the QR screen opens, not now: other mods may not
     * have registered their types yet.
     */
    private static BooleanSupplier customRecipesUnsupported(boolean emiLoaded) {
        Set<String> notCustom = Set.of("minecraft", "jei");
        return emiLoaded
                ? () -> false
                : () -> BuiltInRegistries.RECIPE_TYPE.keySet().stream()
                        .anyMatch(type -> !notCustom.contains(type.getNamespace()));
    }

    /** EMI when it's loaded (ADR 0008); its classes are only reached in that branch. */
    private static Function<ClientLevel, RecipeSource> recipeSources(boolean emiLoaded) {
        return emiLoaded ? level -> new EmiRecipeSource() : VanillaRecipeSource::new;
    }
}
