package com.solace.mixin;

import com.solace.performance.EarthWaterSectionScan;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "potatowolfie.earth_and_water.EarthWater", remap = false)
public abstract class EarthWaterChunkScanMixin {
    // Connector remaps the argument classes to Mojmap/SRG, but keeps this mod method's name.
    @Inject(method = "scheduleBlockTicksForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void solace$scanCandidateSections(ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        if (EarthWaterSectionScan.scan(level, chunk)) ci.cancel();
    }
}
