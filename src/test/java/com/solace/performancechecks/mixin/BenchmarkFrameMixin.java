package com.solace.performancechecks.mixin;

import com.solace.performancechecks.BenchmarkSession;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary runtime instrumentation, excluded from the production JAR. */
@Mixin(Minecraft.class)
public abstract class BenchmarkFrameMixin {
    @Inject(method = "m_91383_", at = @At("RETURN"), remap = false)
    private void solacebench$completedFrame(boolean renderLevel, CallbackInfo ci) {
        BenchmarkSession.completedFrame();
    }
}
