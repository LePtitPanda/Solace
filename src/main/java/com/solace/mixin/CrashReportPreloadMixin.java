package com.solace.mixin;

import com.solace.performance.CrashReportPreloadState;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the startup crash-report warm-up so its graphics-card query can be skipped (see SystemReportGraphicsMixin). */
@Mixin(CrashReport.class)
public abstract class CrashReportPreloadMixin {
    @Inject(method = "preload", at = @At("HEAD"))
    private static void solace$beginPreload(CallbackInfo ci) {
        CrashReportPreloadState.begin();
    }

    @Inject(method = "preload", at = @At("RETURN"))
    private static void solace$endPreload(CallbackInfo ci) {
        CrashReportPreloadState.end();
    }
}
