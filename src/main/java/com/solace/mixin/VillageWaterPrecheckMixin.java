package com.solace.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Skips a village spot when a river or ocean biome lies within the village's reach.
 * Runs before the jigsaw layout, so a skipped spot costs ~100 biome lookups instead of a full
 * village build (keeps VillageSpawnPoint's spawn search fast) and a village is never cut short:
 * it generates whole or not at all.
 */
@Mixin(JigsawStructure.class)
public abstract class VillageWaterPrecheckMixin {
    private static final int SOLACE$RADIUS = 96;
    private static final int SOLACE$STEP = 16;
    private static final int SOLACE$SURFACE_Y = 63;

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void solace$skipVillageNearWater(Structure.GenerationContext context,
                                             CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Structure self = (Structure) (Object) this;
        if (!context.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(self).is(StructureTags.VILLAGE)) {
            return;
        }
        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();
        int qy = QuartPos.fromBlock(SOLACE$SURFACE_Y);
        for (int dx = -SOLACE$RADIUS; dx <= SOLACE$RADIUS; dx += SOLACE$STEP) {
            for (int dz = -SOLACE$RADIUS; dz <= SOLACE$RADIUS; dz += SOLACE$STEP) {
                if (dx * dx + dz * dz > SOLACE$RADIUS * SOLACE$RADIUS) {
                    continue;
                }
                Holder<Biome> biome = context.biomeSource().getNoiseBiome(
                        QuartPos.fromBlock(cx + dx), qy, QuartPos.fromBlock(cz + dz), context.randomState().sampler());
                if (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)) {
                    cir.setReturnValue(Optional.empty());
                    return;
                }
            }
        }
    }
}
