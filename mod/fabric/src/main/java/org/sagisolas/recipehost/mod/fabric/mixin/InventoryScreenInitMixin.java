package org.sagisolas.recipehost.mod.fabric.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.sagisolas.recipehost.mod.fabric.InventoryInitDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends the screen hierarchy only to reach the protected {@code addRenderableWidget}. */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenInitMixin extends AbstractContainerScreen<InventoryMenu> {

    private InventoryScreenInitMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void recipehost$onInit(CallbackInfo ci) {
        InventoryInitDispatcher.dispatch((InventoryScreen) (Object) this, this::addRenderableWidget);
    }
}
