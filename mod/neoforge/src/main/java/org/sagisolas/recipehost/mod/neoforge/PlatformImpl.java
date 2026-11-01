package org.sagisolas.recipehost.mod.neoforge;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.sagisolas.recipehost.mod.common.ModDescriptor;

import java.nio.file.Path;
import java.util.List;
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
}
