package org.sagisolas.recipehost.mod.fabric;

import net.minecraft.client.multiplayer.ClientLevel;

import java.util.function.Consumer;

/** Static sink so the Mixin into {@code Minecraft#setLevel} can fire the common callback. */
public final class WorldJoinDispatcher {

    private static volatile Consumer<ClientLevel> callback;

    private WorldJoinDispatcher() {}

    public static void register(Consumer<ClientLevel> cb) {
        callback = cb;
    }

    public static void dispatch(ClientLevel level) {
        Consumer<ClientLevel> cb = callback;
        if (cb != null && level != null) cb.accept(level);
    }
}
