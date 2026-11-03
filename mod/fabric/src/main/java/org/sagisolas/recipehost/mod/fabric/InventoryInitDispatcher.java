package org.sagisolas.recipehost.mod.fabric;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Static sink so the Mixin into {@code InventoryScreen#init} can fire the common callback. */
public final class InventoryInitDispatcher {

    private static volatile BiConsumer<InventoryScreen, Consumer<AbstractWidget>> callback;

    private InventoryInitDispatcher() {}

    public static void register(BiConsumer<InventoryScreen, Consumer<AbstractWidget>> cb) {
        callback = cb;
    }

    public static void dispatch(InventoryScreen screen, Consumer<AbstractWidget> addWidget) {
        BiConsumer<InventoryScreen, Consumer<AbstractWidget>> cb = callback;
        if (cb != null) cb.accept(screen, addWidget);
    }
}
