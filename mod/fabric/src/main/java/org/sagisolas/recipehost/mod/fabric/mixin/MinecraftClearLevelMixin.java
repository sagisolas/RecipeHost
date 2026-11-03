package org.sagisolas.recipehost.mod.fabric.mixin;

import net.minecraft.client.Minecraft;
import org.sagisolas.recipehost.mod.fabric.WorldLeaveDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftClearLevelMixin {

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void recipehost$onClearLevel(CallbackInfo ci) {
        WorldLeaveDispatcher.dispatch();
    }
}
