package com.solace.mixin;

import com.solace.performance.CrashReportPreloadState;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla CrashReport.preload() builds a throwaway system report during startup to warm crash-report
 * classes. Its graphics section asks OSHI for graphics cards, which runs the Windows WMI query
 * Win32_VideoController; on some machines that query takes ~18 s (measured here; Embeddium runs the same
 * query once more for its driver checks). The warm-up report is discarded, so only its graphics
 * section is skipped; real crash reports still list the graphics cards.
 */
@Mixin(SystemReport.class)
public abstract class SystemReportGraphicsMixin {
    @Inject(method = "lambda$putHardware$5(Loshi/hardware/HardwareAbstractionLayer;)V", at = @At("HEAD"), cancellable = true)
    private void solace$skipGraphicsDuringPreload(oshi.hardware.HardwareAbstractionLayer hardware, CallbackInfo ci) {
        if (CrashReportPreloadState.active()) ci.cancel();
    }
}
