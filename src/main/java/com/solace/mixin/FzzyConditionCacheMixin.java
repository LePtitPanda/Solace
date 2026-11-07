package com.solace.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fzzy Config's ValidatedCondition#get walks a synchronized java.util.Vector of condition suppliers on
 * every call. Subtle Effects reads several conditioned settings for every animated block it visits
 * (~10 calls per FireflyManager#tick, ~1,300 visits per client tick), so the render thread spends
 * measurable time re-evaluating config conditions that only change when a setting is edited.
 * Render-thread results are reused for one ~67 ms window (about one client tick); other threads,
 * including the integrated server, keep evaluating every call.
 */
@Pseudo
@Mixin(targets = "me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition", remap = false)
public abstract class FzzyConditionCacheMixin {
    @Unique private long solace$window = Long.MIN_VALUE;
    @Unique private Object solace$value;

    @Unique
    private static long solace$currentWindow() {
        return System.nanoTime() >>> 26;
    }

    @Inject(method = "get()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private void solace$reuse(CallbackInfoReturnable<Object> cir) {
        if (RenderSystem.isOnRenderThread() && solace$window == solace$currentWindow()) {
            cir.setReturnValue(solace$value);
        }
    }

    @Inject(method = "get()Ljava/lang/Object;", at = @At("RETURN"))
    private void solace$remember(CallbackInfoReturnable<Object> cir) {
        if (RenderSystem.isOnRenderThread()) {
            solace$value = cir.getReturnValue();
            solace$window = solace$currentWindow();
        }
    }
}
