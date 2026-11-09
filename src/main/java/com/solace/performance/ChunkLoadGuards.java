package com.solace.performance;

import com.solace.config.PerformanceConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

/**
 * Runtime gate shared by the ServerCore ports; keeps client and worldgen readers unchanged.
 * Callers use Forge's nonblocking ServerChunkCache.getChunkNow, retaining its loading workaround
 * without accessing private chunk holders or adding tickets. See docs/servercore-chunk-load-guards.md.
 */
public final class ChunkLoadGuards {
    private ChunkLoadGuards() { }

    public static boolean enabled(LevelReader level) {
        return level instanceof ServerLevel serverLevel
                && PerformanceConfig.isChunkLoadGuardsEnabled(serverLevel.getServer());
    }
}
