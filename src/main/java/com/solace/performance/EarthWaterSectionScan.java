package com.solace.performance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class EarthWaterSectionScan {
    private EarthWaterSectionScan() { }

    /** False leaves the original handler intact if any of its five blocks cannot be resolved. */
    public static boolean scan(ServerLevel level, LevelChunk chunk) {
        if (Targets.BLOCKS.isEmpty()) return false;

        LevelChunkSection[] sections = chunk.getSections();
        int[] candidates = null;
        int candidateCount = 0;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(Targets.CONTAINS)) continue;
            if (candidates == null) candidates = new int[sections.length];
            candidates[candidateCount++] = i;
        }
        if (candidates == null) return true;

        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = minY + level.getHeight();
        // Retain the original x/z/y tick insertion order, including across section boundaries.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int candidate = 0; candidate < candidateCount; candidate++) {
                    int index = candidates[candidate];
                    LevelChunkSection section = sections[index];
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
                    for (int y = Math.max(0, minY - baseY); y < Math.min(16, maxY - baseY); y++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (Targets.CONTAINS.test(state)) {
                            level.scheduleTick(new BlockPos(baseX + x, baseY + y, baseZ + z), state.getBlock(), 2);
                        }
                    }
                }
            }
        }
        return true;
    }

    // First touched by the chunk-load callback, after block registration has completed.
    private static final class Targets {
        private static final Set<Block> BLOCKS = resolve();
        private static final Predicate<BlockState> CONTAINS = state -> BLOCKS.contains(state.getBlock());

        private static Set<Block> resolve() {
            Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<>());
            for (String name : List.of("chiseled_dark_prismarine", "chiseled_prismarine_bricks",
                    "chiseled_dark_dripstone_bricks", "chiseled_dripstone_bricks", "reinforced_spawner")) {
                ResourceLocation id = new ResourceLocation("earth-and-water", name);
                Block block = ForgeRegistries.BLOCKS.getValue(id);
                if (block == null || block == Blocks.AIR) return Collections.emptySet();
                blocks.add(block);
            }
            return blocks;
        }
    }
}
