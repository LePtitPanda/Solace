package com.solace.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;

/**
 * Publishes the home village to structure-generation threads.
 *
 * <p>The biome-source identity limits the check to the loaded Overworld whose
 * village was resolved. If the target has not been established yet, structure
 * generation is intentionally left untouched.</p>
 */
public final class IntroVillageStructureProtection {
    private static final int PROTECTION_RADIUS_BLOCKS = 300;
    private static final long PROTECTION_RADIUS_SQUARED =
            (long) PROTECTION_RADIUS_BLOCKS * PROTECTION_RADIUS_BLOCKS;

    private static volatile Target target;

    private IntroVillageStructureProtection() {
    }

    public static void setTarget(ServerLevel level, BlockPos villagePos) {
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) {
            return;
        }

        target = new Target(
                level.getSeed(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                villagePos.immutable()
        );
    }

    public static void clearTarget(ServerLevel level) {
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) {
            return;
        }

        Target current = target;
        if (current != null
                && current.biomeSource() == level.getChunkSource().getGenerator().getBiomeSource()) {
            target = null;
        }
    }

    public static void reset() {
        target = null;
    }

    public static boolean shouldPreventStructure(BiomeSource biomeSource, long seed, ChunkPos chunkPos) {
        Target current = target;
        if (current == null || current.seed() != seed || current.biomeSource() != biomeSource) {
            return false;
        }

        long dx = (long) chunkPos.getMiddleBlockX() - current.villagePos().getX();
        long dz = (long) chunkPos.getMiddleBlockZ() - current.villagePos().getZ();
        return dx * dx + dz * dz <= PROTECTION_RADIUS_SQUARED;
    }

    private record Target(long seed, BiomeSource biomeSource, BlockPos villagePos) {
    }
}
