package com.solace.performancechecks.mixin;

import com.solace.performancechecks.BenchmarkSession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds song-selection transitions constant only during the disposable benchmark. */
@Pseudo
@Mixin(targets = "circuitlord.reactivemusic.ReactiveMusic", remap = false)
public abstract class BenchmarkMusicMixin {
    @Inject(method = "newTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void solacebench$freezeMusic(CallbackInfo ci) {
        if (BenchmarkSession.freezeMusic) ci.cancel();
    }
}
