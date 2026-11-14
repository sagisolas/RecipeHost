/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.fabric;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.sagisolas.recipehost.mod.ModDescriptor;

@Environment(EnvType.CLIENT)
public final class PlatformImpl {

    private PlatformImpl() {}

    public static List<ModDescriptor> listMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> new ModDescriptor(
                        mod.getMetadata().getId(),
                        mod.getMetadata().getName(),
                        mod.getMetadata().getVersion().getFriendlyString()))
                .toList();
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public static void onScreenInit(BiConsumer<Screen, Consumer<AbstractWidget>> callback) {
        ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) -> callback.accept(screen, Screens.getButtons(screen)::add));
    }

    public static void onWorldLeave(Runnable callback) {
        // Fires on every disconnect, singleplayer included. Minecraft#clearClientLevel did not.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> callback.run());
    }

    /**
     * fabric-api has no end-of-frame event, so this rides the GUI passes: the HUD in a world and
     * each screen's render on menus, each flushing its own {@link GuiGraphics} first. A frame
     * with both runs {@code callback} once.
     */
    public static void onFrame(Runnable callback) {
        OncePerFrame frame = new OncePerFrame(callback);
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> frame.afterHud(graphics));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ScreenEvents.afterRender(screen)
                .register((rendered, graphics, mouseX, mouseY, partialTick) -> frame.afterScreen(graphics)));
    }

    /**
     * Runs a callback once per frame from the HUD and screen passes. The HUD draws before a screen
     * in the same frame, so a screen pass right after a HUD pass skips; without a HUD (on menus,
     * or with the GUI hidden) every screen pass runs it.
     */
    private static final class OncePerFrame {
        private final Runnable callback;
        private boolean ranInHud;

        OncePerFrame(Runnable callback) {
            this.callback = callback;
        }

        void afterHud(GuiGraphics graphics) {
            run(graphics);
            ranInHud = true;
        }

        void afterScreen(GuiGraphics graphics) {
            if (ranInHud) {
                ranInHud = false;
            } else {
                run(graphics);
            }
        }

        private void run(GuiGraphics graphics) {
            // The pass's vertices are still buffered in Minecraft's shared buffers; drawn later,
            // they would land in whatever render target the callback binds.
            graphics.flush();
            callback.run();
        }
    }
}
