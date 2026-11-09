package com.solace.mixin.chunkguards;

import com.solace.performance.ChunkLoadGuards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port of Wesley1808's ServerCore optimizations/sync_loads/MapItemMixin (MIT).
 * Source: ServerCore 1.20.1, d1d0a02d; see META-INF/licenses/servercore-MIT.txt.
 */
@Mixin(MapItem.class)
public abstract class MapItemChunkGuardMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk solace$onlyMapLoadedChunks(Level level, int x, int z) {
        return ChunkLoadGuards.enabled(level)
                ? ((ServerLevel) level).getChunkSource().getChunkNow(x, z)
                : level.getChunk(x, z);
    }

    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;isEmpty()Z"))
    private boolean solace$skipMissingMapChunks(LevelChunk chunk) {
        // Both redirects are required together: a missing chunk leaves existing pixels untouched.
        return chunk == null || chunk.isEmpty();
    }
}
