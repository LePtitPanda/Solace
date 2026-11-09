package com.solace.mixin.chunkguards;

import com.solace.performance.ChunkLoadGuards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port of Wesley1808's ServerCore optimizations/sync_loads/DynamicGameEventListenerMixin (MIT).
 * Source: ServerCore 1.20.1, d1d0a02d; see META-INF/licenses/servercore-MIT.txt.
 */
@Mixin(DynamicGameEventListener.class)
public abstract class DynamicGameEventChunkGuardMixin {
    @Redirect(method = "ifChunkExists", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private static ChunkAccess solace$onlyUpdateLoadedListeners(LevelReader level, int x, int z, ChunkStatus status, boolean create) {
        // Even getChunk(..., false) can wait for an existing ticket's unfinished future.
        return ChunkLoadGuards.enabled(level)
                ? ((ServerLevel) level).getChunkSource().getChunkNow(x, z)
                : level.getChunk(x, z, status, create);
    }
}
