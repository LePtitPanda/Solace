package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.solace.audit.AuditSession;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StructureStart.class)
public abstract class StructurePlacementAudit {
    @WrapMethod(method = "placeInChunk")
    private void audit$place(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos pos, Operation<Void> original) {
        try (AuditSession.Ticket ticket = AuditSession.open("structure_placement")) {
            StructureStart self = (StructureStart) (Object) this;
            if (ticket != null) ticket.begin(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(self.getStructure()),
                    self.getStructure().getClass(), level.getLevel().dimension().location(), pos);
            original.call(level, manager, generator, random, box, pos);
        }
    }
}
