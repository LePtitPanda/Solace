package com.solace.mixin.chunkguards;

import com.solace.performance.ChunkLoadGuards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port of Wesley1808's ServerCore optimizations/sync_loads/RemoveBlockGoalMixin (MIT).
 * Source: ServerCore 1.20.1, d1d0a02d; see META-INF/licenses/servercore-MIT.txt.
 */
@Mixin(RemoveBlockGoal.class)
public abstract class RemoveBlockGoalChunkGuardMixin {
    @Redirect(method = "isValidTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess solace$onlyCheckLoadedTargets(LevelReader level, int x, int z, ChunkStatus status, boolean create) {
        // Vanilla already handles null. Keep Forge's canEntityDestroy hook in the original method.
        return ChunkLoadGuards.enabled(level)
                ? ((ServerLevel) level).getChunkSource().getChunkNow(x, z)
                : level.getChunk(x, z, status, create);
    }
}
