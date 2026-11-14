/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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

    /**
     * Fires on the render thread once per rendered frame, in a world and on menus, after the
     * frame's GUI so far is flushed: code there may draw into its own render target with
     * Minecraft's shared buffers. It runs outside Minecraft's task queue, so code there may wait
     * on {@code Minecraft#submit}. On Fabric it rides the HUD and screen passes, so it doesn't
     * fire while the GUI is hidden (F1) with no screen open.
     */
    @ExpectPlatform
    public static void onFrame(Runnable callback) {
        throw new AssertionError("@ExpectPlatform not wired");
    }
}
