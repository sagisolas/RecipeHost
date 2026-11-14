/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.neoforge;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.sagisolas.recipehost.mod.ModDescriptor;

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

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public static void onScreenInit(BiConsumer<Screen, Consumer<AbstractWidget>> callback) {
        NeoForge.EVENT_BUS.addListener(
                ScreenEvent.Init.Post.class, event -> callback.accept(event.getScreen(), event::addListener));
    }

    public static void onWorldLeave(Runnable callback) {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> callback.run());
    }

    public static void onFrame(Runnable callback) {
        // After GameRenderer#render: the frame, GUI included, is drawn and flushed.
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, event -> callback.run());
    }
}
