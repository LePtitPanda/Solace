package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.solace.audit.AuditSession;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class StructureGenerationAudit {
    @WrapMethod(method = "generate")
    private StructureStart audit$generate(RegistryAccess registries, ChunkGenerator generator, BiomeSource biomes, RandomState state,
            StructureTemplateManager templates, long seed, ChunkPos pos, int references, LevelHeightAccessor heights, Predicate<Holder<Biome>> valid, Operation<StructureStart> original) {
        try (AuditSession.Ticket ticket = AuditSession.open("structure_generation")) {
            if (ticket != null) ticket.begin(registries.registryOrThrow(Registries.STRUCTURE).getKey((Structure) (Object) this), getClass(), "worldgen", pos);
            return original.call(registries, generator, biomes, state, templates, seed, pos, references, heights, valid);
        }
    }
}
