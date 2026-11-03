package org.sagisolas.recipehost.mod.fabric;

/** Static sink so the Mixin into {@code Minecraft#clearClientLevel} can fire the common callback. */
public final class WorldLeaveDispatcher {

    private static volatile Runnable callback;

    private WorldLeaveDispatcher() {}

    public static void register(Runnable cb) {
        callback = cb;
    }

    public static void dispatch() {
        Runnable cb = callback;
        if (cb != null) cb.run();
    }
}
