package org.sagisolas.recipehost.mod.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import org.sagisolas.recipehost.mod.ModDescriptor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    // TODO: client-started lifecycle hook without fabric-api (Mixin into Minecraft#<init> or #run).
    public static void onClientStarted(Runnable callback) {
        throw new UnsupportedOperationException("onClientStarted: wire a Mixin instead of fabric-api");
    }

    // TODO: keybinding registration without fabric-api (Mixin into KeyMapping.ALL or Options).
    public static void registerKeybinding(KeyMapping keyMapping) {
        throw new UnsupportedOperationException("registerKeybinding: wire a Mixin instead of fabric-api");
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public static void onWorldJoin(Consumer<ClientLevel> callback) {
        WorldJoinDispatcher.register(callback);
    }
}
