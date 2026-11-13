package com.solace.mixin;

import com.faboslav.structurify.common.world.level.structure.StructurePlacementContext;
import com.solace.world.gates.StructureGates;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Structurify's multi-attempt placement picks a chunk by test-generating; a closed gate must not win it. */
@Pseudo
@Mixin(targets = "com.faboslav.structurify.common.world.level.structure.StructurePlacementResolver", remap = false)
public abstract class StructurifyPlacementResolverMixin {
    @Inject(method = "generateStructure", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void solace$gateClosed(StructureSet.StructureSelectionEntry entry, ChunkPos chunkPos,
                                          StructurePlacementContext context, CallbackInfoReturnable<StructureStart> cir) {
        if (!StructureGates.mayGenerate(entry.structure())) {
            cir.setReturnValue(StructureStart.INVALID_START);
        }
    }
}
