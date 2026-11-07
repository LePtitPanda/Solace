package com.solace.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dynamic FPS throttles an unfocused window to 1 FPS (15-iteration floor) and a minimised one to 0,
 * including during the resource-loading screen. Reload steps that hand off to the render thread then
 * advance once per throttled frame: a hidden test launch spent 88 s in the final reload stage that
 * takes 8-15 s in a focused window. Players commonly alt-tab during the multi-minute modpack start, so
 * while Minecraft's LoadingOverlay is showing, Dynamic FPS reports the normal frame limit and renders
 * every frame. Throttling in menus and in-game is unchanged.
 */
@Pseudo
@Mixin(targets = "dynamic_fps.impl.DynamicFPSMod", remap = false)
public abstract class DynamicFpsLoadingMixin {
    @Unique
    private static boolean solace$loading() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.getOverlay() instanceof LoadingOverlay;
    }

    @Inject(method = "targetFrameRate()I", at = @At("HEAD"), cancellable = true)
    private static void solace$fullRateWhileLoading(CallbackInfoReturnable<Integer> cir) {
        if (solace$loading()) cir.setReturnValue(Minecraft.getInstance().options.framerateLimit().get());
    }

    @Inject(method = "checkForRender()Z", at = @At("HEAD"), cancellable = true)
    private static void solace$renderWhileLoading(CallbackInfoReturnable<Boolean> cir) {
        if (solace$loading()) cir.setReturnValue(true);
    }
}
