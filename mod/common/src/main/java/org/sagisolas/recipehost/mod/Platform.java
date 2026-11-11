package org.sagisolas.recipehost.mod;

import dev.architectury.injectables.annotations.ExpectPlatform;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * Loader-agnostic access to the handful of operations the companion mod can't do
 * portably against Mojmap-named Minecraft APIs. Each static here is wired at
 * compile time to a per-loader implementation (see :mod:fabric and :mod:neoforge).
 *
 * See docs/adr/0005-architectury-loom-for-loader-abstraction.md.
 */
public final class Platform {

    private Platform() {}

    @ExpectPlatform
    public static List<ModDescriptor> listMods() {
        throw new AssertionError("@ExpectPlatform not wired");
    }

    @ExpectPlatform
    public static String minecraftVersion() {
        throw new AssertionError("@ExpectPlatform not wired");
    }

    /**
     * Fires after any {@link Screen} has initialised its widgets (on open and on resize).
     * The second argument adds a widget to that screen.
     */
    @ExpectPlatform
    public static void onScreenInit(BiConsumer<Screen, Consumer<AbstractWidget>> callback) {
        throw new AssertionError("@ExpectPlatform not wired");
    }

    /** Fires when the player leaves the current world or server. */
    @ExpectPlatform
    public static void onWorldLeave(Runnable callback) {
        throw new AssertionError("@ExpectPlatform not wired");
    }
}
