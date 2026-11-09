package com.solace.mixin;

import com.solace.performance.DensityFunctionHashMemo;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkHashMemoMixin {
    @Shadow @Final @Mutable
    private Map<DensityFunction, DensityFunction> wrapped;

    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"),
            require = 1, allow = 1)
    private void solace$useHashMemoWrapCache(CallbackInfo ci) {
        // After all field initializers (including ModernFix's map replacement), before the first wrap.
        this.wrapped = DensityFunctionHashMemo.newWrappingMap();
    }
}
