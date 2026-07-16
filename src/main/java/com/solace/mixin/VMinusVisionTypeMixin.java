package com.solace.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Reuses VMinus' live unmodifiable visions view instead of allocating a new
 * {@code Collections.UnmodifiableMap} wrapper for every block-property query.
 *
 * <p>The cached object is still the view created by VMinus itself, so changes
 * to the mutable backing map during a data-pack reload remain visible.</p>
 */
@Pseudo
@Mixin(targets = "net.lixir.vminus.vision.VisionType", remap = false)
public abstract class VMinusVisionTypeMixin {
    @Unique
    private volatile Map<?, ?> solace$cachedVisionsView;

    @Inject(method = "getVisions", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void solace$returnCachedVisionsView(CallbackInfoReturnable<Map<?, ?>> cir) {
        Map<?, ?> cachedView = solace$cachedVisionsView;
        if (cachedView != null) {
            cir.setReturnValue(cachedView);
        }
    }

    @Inject(method = "getVisions", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void solace$rememberVisionsView(CallbackInfoReturnable<Map<?, ?>> cir) {
        Map<?, ?> cachedView = solace$cachedVisionsView;
        if (cachedView == null) {
            solace$cachedVisionsView = cir.getReturnValue();
        } else {
            // A concurrent first call may already have installed the canonical
            // view while this invocation was running VMinus' original method.
            cir.setReturnValue(cachedView);
        }
    }
}
