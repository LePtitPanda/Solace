package com.solace.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.function.Predicate;

/**
 * Finds where a building (a jigsaw template, e.g. a {@code ONCE} building from village-buildings.json) was placed
 * in a village, by reading the village's own structure pieces. Nothing is recorded at worldgen.
 */
public final class VillageBuildingLocator {
    private VillageBuildingLocator() {
    }

    /** The bounding box of the first piece using {@code template} in the village that has a piece at {@code near}. */
    public static BoundingBox find(ServerLevel level, BlockPos near, ResourceLocation template) {
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(near),
                structure -> structures.wrapAsHolder(structure).is(StructureTags.VILLAGE))) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof PoolElementStructurePiece pool
                        && JigsawVillageBuildingTool.templateId(pool.getElement()).filter(template::equals).isPresent()) {
                    return piece.getBoundingBox();
                }
            }
        }
        return null;
    }

    /** A block inside {@code box} that matches, or null. */
    public static BlockPos findBlock(ServerLevel level, BoundingBox box, Predicate<BlockInWorld> block) {
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (block.test(new BlockInWorld(level, pos, true))) {
                return pos.immutable();
            }
        }
        return null;
    }
}
