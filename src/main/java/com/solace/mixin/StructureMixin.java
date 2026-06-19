package com.solace.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Structure.class)
public class StructureMixin {

    @Inject(
            method = "findValidGenerationPoint",
            at = @At("HEAD"),
            cancellable = true
    )
    private void solace$preventSpawnNearVillage(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Structure structure = (Structure) (Object) this;
        ResourceLocation id = context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);

        // If the structure is a village, do not restrict it
        if (id != null && id.getPath().contains("village")) {
            return;
        }

        ChunkPos currentChunk = context.chunkPos();
        long seed = context.seed();

        // Create the structure state for calculations
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(
                context.randomState(),
                seed,
                context.biomeSource(),
                context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
        );

        // Find all structure sets that contain a village
        for (Holder<StructureSet> structureSet : context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).holders().toList()) {
            if (!structureSet.isBound()) continue;

            // Check if this structure set contains any village structure
            boolean hasVillage = false;
            for (StructureSet.StructureSelectionEntry entry : structureSet.value().structures()) {
                if (entry.structure().unwrapKey().map(key -> key.location().getPath().contains("village")).orElse(false)) {
                    hasVillage = true;
                    break;
                }
            }

            if (!hasVillage) continue;

            // Get the structure placement
            StructurePlacement placement = structureSet.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement spreadPlacement) {
                int spacing = spreadPlacement.spacing();

                // Radius in chunks (600 blocks = 37.5 chunks -> 38 chunks)
                int radius = 38;

                int minChunkX = currentChunk.x - radius;
                int maxChunkX = currentChunk.x + radius;
                int minChunkZ = currentChunk.z - radius;
                int maxChunkZ = currentChunk.z + radius;

                int minCellX = Math.floorDiv(minChunkX, spacing);
                int maxCellX = Math.floorDiv(maxChunkX, spacing);
                int minCellZ = Math.floorDiv(minChunkZ, spacing);
                int maxCellZ = Math.floorDiv(maxChunkZ, spacing);

                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        ChunkPos potPos = spreadPlacement.getPotentialStructureChunk(seed, cellX * spacing, cellZ * spacing);

                        // Check if this potential chunk is within our 38-chunk radius
                        if (potPos.x >= minChunkX && potPos.x <= maxChunkX && potPos.z >= minChunkZ && potPos.z <= maxChunkZ) {
                            // Check if the village actually spawns at this chunk
                            if (placement.isStructureChunk(state, potPos.x, potPos.z)) {
                                // Calculate distance in blocks
                                double dx = (potPos.getMiddleBlockX() - currentChunk.getMiddleBlockX());
                                double dz = (potPos.getMiddleBlockZ() - currentChunk.getMiddleBlockZ());
                                double distSq = dx * dx + dz * dz;

                                if (distSq <= 600.0 * 600.0) {
                                    // Village found within 600 blocks! Cancel spawning.
                                    cir.setReturnValue(Optional.empty());
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
