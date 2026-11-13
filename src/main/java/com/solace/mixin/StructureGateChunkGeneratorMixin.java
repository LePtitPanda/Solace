package com.solace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import com.solace.world.gates.StructureGates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structure gates in vanilla worldgen and locate. These run inside Structurify's own wrappers of the
 * same methods, so its other checks still apply; its is_disabled is overridden for gated structures
 * by StructurifyStructureDataMixin.
 */
@Mixin(ChunkGenerator.class)
public abstract class StructureGateChunkGeneratorMixin {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void solace$gateClosed(StructureSet.StructureSelectionEntry entry, StructureManager structureManager,
                                   RegistryAccess registryAccess, RandomState randomState,
                                   StructureTemplateManager templateManager, long seed, ChunkAccess chunk,
                                   ChunkPos chunkPos, SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        if (!StructureGates.mayGenerate(entry.structure())) {
            cir.setReturnValue(false);
        }
    }

    /** The once-claim happens only for a start that is really about to be placed. */
    @WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;isValid()Z"))
    private boolean solace$claimGate(StructureStart start, Operation<Boolean> original,
                                     @Local(argsOnly = true) StructureSet.StructureSelectionEntry entry,
                                     @Local(argsOnly = true) StructureManager structureManager) {
        return original.call(start) && StructureGates.claim(entry.structure(),
                ((StructureManagerAccessor) structureManager).solace$level(), start);
    }

    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void solace$gatedLocate(ServerLevel level, HolderSet<Structure> structures, BlockPos from, int radius,
                                    boolean skipKnown, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (StructureGates.affects(structures)) {
            cir.setReturnValue(StructureGates.locate((ChunkGenerator) (Object) this, level, structures, from, radius,
                    skipKnown));
        }
    }
}
