package org.sagisolas.recipehost.mod.common;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.KeyMapping;

import java.nio.file.Path;
import java.util.List;

/**
 * Loader-agnostic access to the handful of operations the companion mod can't do
 * portably against Mojmap-named Minecraft APIs. Each static here is wired at
 * compile time to a per-loader implementation (see :mod:fabric).
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
    public static Path configDir() {
        throw new AssertionError("@ExpectPlatform not wired");
    }

    @ExpectPlatform
    public static void onClientStarted(Runnable callback) {
        throw new AssertionError("@ExpectPlatform not wired");
    }

    @ExpectPlatform
    public static void registerKeybinding(KeyMapping keyMapping) {
        throw new AssertionError("@ExpectPlatform not wired");
    }

    @ExpectPlatform
    public static String minecraftVersion() {
        throw new AssertionError("@ExpectPlatform not wired");
    }
}
