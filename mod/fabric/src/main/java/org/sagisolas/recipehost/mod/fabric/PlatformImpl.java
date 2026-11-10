package org.sagisolas.recipehost.mod.fabric;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
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
}
