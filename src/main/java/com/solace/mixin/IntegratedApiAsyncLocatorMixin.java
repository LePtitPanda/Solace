package com.solace.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Integrated API's locator alive through Minecraft's final world save. */
@Pseudo
@Mixin(targets = "com.craisinlord.integrated_api.utils.AsyncLocator", remap = false)
public abstract class IntegratedApiAsyncLocatorMixin {
    @Inject(method = "handleServerStoppingEvent", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void solace$deferShutdownUntilAfterWorldSave(CallbackInfo ci) {
        ci.cancel();
    }
}
