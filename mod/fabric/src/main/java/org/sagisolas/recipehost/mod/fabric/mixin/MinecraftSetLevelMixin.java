package org.sagisolas.recipehost.mod.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.sagisolas.recipehost.mod.fabric.WorldJoinDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftSetLevelMixin {

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void recipehost$onSetLevel(ClientLevel level, ReceivingLevelScreen.Reason reason, CallbackInfo ci) {
        WorldJoinDispatcher.dispatch(level);
    }
}
