package org.sagisolas.recipehost.mod.neoforge;

import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.sagisolas.recipehost.mod.ModDescriptor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public final class PlatformImpl {

    private PlatformImpl() {}

    public static List<ModDescriptor> listMods() {
        return ModList.get().getMods().stream()
                .map(info -> new ModDescriptor(
                        info.getModId(),
                        info.getDisplayName(),
                        info.getVersion().toString()))
                .collect(Collectors.toUnmodifiableList());
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    // TODO: client-started lifecycle hook — use NeoForge's FMLClientSetupEvent or ClientTickEvent (no extra deps).
    public static void onClientStarted(Runnable callback) {
        throw new UnsupportedOperationException("onClientStarted: wire NeoForge client lifecycle event");
    }

    // TODO: keybinding registration — subscribe to RegisterKeyMappingsEvent.
    public static void registerKeybinding(KeyMapping keyMapping) {
        throw new UnsupportedOperationException("registerKeybinding: wire RegisterKeyMappingsEvent");
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    // TODO: wire ClientPlayerNetworkEvent.LoggingIn (or LevelEvent.Load) when NeoForge dev work starts.
    public static void onWorldJoin(Consumer<ClientLevel> callback) {
        // No-op on NeoForge until the loader slice lands. Dev harness is Fabric-only for now.
    }

    public static void onScreenInit(BiConsumer<Screen, Consumer<AbstractWidget>> callback) {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class,
                event -> callback.accept(event.getScreen(), event::addListener));
    }

    public static void onWorldLeave(Runnable callback) {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> callback.run());
    }
}
