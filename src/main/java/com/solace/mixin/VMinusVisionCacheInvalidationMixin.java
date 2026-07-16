package com.solace.mixin;

import com.solace.performance.VMinusIngredientFilterCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.lixir.vminus.vision.Vision", remap = false)
public abstract class VMinusVisionCacheInvalidationMixin {
    @Inject(method = "resetVisions()V", at = @At("HEAD"), require = 0, remap = false)
    private static void solace$resetIngredientFilterCache(CallbackInfo ci) {
        VMinusIngredientFilterCache.reset();
    }

    @Inject(
            method = "getOrAddVision(Lnet/lixir/vminus/vision/VisionType;Lnet/minecraft/resources/ResourceLocation;Lnet/lixir/vminus/vision/Vision;)Lnet/lixir/vminus/vision/Vision;",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void solace$invalidateAfterVisionMutation(CallbackInfoReturnable<Object> cir) {
        VMinusIngredientFilterCache.markVisionDataChanged();
    }
}
