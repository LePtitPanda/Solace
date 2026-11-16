package com.solace.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

/**
 * Keeps cave/canyon carvers out of village pieces so floors and streets are not left over holes.
 * Only columns under a village piece are protected, from a few blocks below the piece to its top;
 * caves deeper down are untouched. Uses the carving mask's "already carved" check, so no extra
 * block work is done and chunks without a village return after one reference lookup.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class VillageCarverGuardMixin {
    private static final int SOLACE$DEPTH_BELOW_PIECE = 6;
    private static final int SOLACE$HORIZONTAL_MARGIN = 2;

    @Inject(method = "applyCarvers", at = @At("HEAD"))
    private void solace$protectVillagePieces(WorldGenRegion region, long seed, RandomState randomState,
                                             BiomeManager biomeManager, StructureManager structureManager,
                                             ChunkAccess chunk, GenerationStep.Carving step, CallbackInfo ci) {
        if (!(chunk instanceof ProtoChunk protoChunk) || !chunk.hasAnyStructureReferences()) {
            return;
        }
        var structures = region.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkPos pos = chunk.getPos();
        List<StructureStart> starts = structureManager.startsForStructure(pos,
                structure -> structures.wrapAsHolder(structure).is(StructureTags.VILLAGE));
        if (starts.isEmpty()) {
            return;
        }

        // Per column (x | z << 4): protected Y range, empty when lo > hi.
        int[] lo = new int[256];
        int[] hi = new int[256];
        Arrays.fill(lo, Integer.MAX_VALUE);
        Arrays.fill(hi, Integer.MIN_VALUE);
        boolean any = false;
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        for (StructureStart start : starts) {
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox box = piece.getBoundingBox();
                int x0 = Math.max(box.minX() - SOLACE$HORIZONTAL_MARGIN - minX, 0);
                int x1 = Math.min(box.maxX() + SOLACE$HORIZONTAL_MARGIN - minX, 15);
                int z0 = Math.max(box.minZ() - SOLACE$HORIZONTAL_MARGIN - minZ, 0);
                int z1 = Math.min(box.maxZ() + SOLACE$HORIZONTAL_MARGIN - minZ, 15);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        int i = x | z << 4;
                        lo[i] = Math.min(lo[i], box.minY() - SOLACE$DEPTH_BELOW_PIECE);
                        hi[i] = Math.max(hi[i], box.maxY());
                        any = true;
                    }
                }
            }
        }
        if (!any) {
            return;
        }

        CarvingMask mask = protoChunk.getOrCreateCarvingMask(step);
        CarvingMask.Mask previous = ((CarvingMaskAccessor) mask).solace$getAdditionalMask();
        mask.setAdditionalMask((x, y, z) -> {
            int i = (x & 15) | (z & 15) << 4;
            return (y >= lo[i] && y <= hi[i]) || previous.test(x, y, z);
        });
    }
}
