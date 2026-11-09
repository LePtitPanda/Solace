package com.solace.mixin.chunkguards;

import com.solace.performance.ChunkLoadGuards;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Port of Wesley1808's ServerCore optimizations/sync_loads/SleepInBedMixin (MIT).
 * Source: ServerCore 1.20.1, d1d0a02d; see META-INF/licenses/servercore-MIT.txt.
 * Uses the HOME memory at HEAD instead of capturing fragile local variables.
 */
@Mixin(SleepInBed.class)
public abstract class SleepInBedChunkGuardMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void solace$onlyCheckLoadedBeds(ServerLevel level, LivingEntity owner, CallbackInfoReturnable<Boolean> cir) {
        if (ChunkLoadGuards.enabled(level)) {
            Optional<GlobalPos> home = owner.getBrain().getMemory(MemoryModuleType.HOME);
            if (home.isPresent() && level.getChunkSource().getChunkNow(
                    home.get().pos().getX() >> 4, home.get().pos().getZ() >> 4) == null) {
                cir.setReturnValue(false);
            }
        }
    }
}
