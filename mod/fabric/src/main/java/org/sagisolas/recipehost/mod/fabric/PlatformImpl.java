package org.sagisolas.recipehost.mod.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.sagisolas.recipehost.mod.ModDescriptor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static void onClientStarted(Runnable callback) {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> callback.run());
    }

    public static void registerKeybinding(KeyMapping keyMapping) {
        KeyBindingHelper.registerKeyBinding(keyMapping);
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public static void onWorldJoin(Consumer<ClientLevel> callback) {
        // fabric-api fires this at the TAIL of Minecraft#setLevel, non-null levels only: on joining a
        // world or server and on changing dimension (a same-dimension respawn keeps its level).
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> callback.accept(level));
    }

    public static void onScreenInit(BiConsumer<Screen, Consumer<AbstractWidget>> callback) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                callback.accept(screen, Screens.getButtons(screen)::add));
    }

    public static void onWorldLeave(Runnable callback) {
        // Fires on every disconnect, singleplayer included. Minecraft#clearClientLevel did not.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> callback.run());
    }
}
