package com.solace.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class NaturalSurfaceSpawnSupport {
    private NaturalSurfaceSpawnSupport() {
    }

    public static boolean hasNaturalSkyAccess(Level level, BlockPos spawnPos) {
        if (level.canSeeSky(spawnPos)) {
            return true;
        }

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, spawnPos.getX(), spawnPos.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(spawnPos.getX(), spawnPos.getY() + 1, spawnPos.getZ());
        boolean foundCanopy = false;

        for (int y = spawnPos.getY() + 1; y < surfaceY; y++) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || state.getLightBlock(level, cursor) == 0) {
                continue;
            }

            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                foundCanopy = true;
                continue;
            }

            return false;
        }

        return foundCanopy;
    }
}
