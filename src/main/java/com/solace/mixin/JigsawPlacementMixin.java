package com.solace.mixin;

import com.solace.world.JigsawVillageBuildingTool;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JigsawPlacement.class)
public abstract class JigsawPlacementMixin {
    @Inject(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/RandomState;IZLnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Ljava/util/List;Lnet/minecraft/world/phys/shapes/VoxelShape;)V",
            at = @At("HEAD")
    )
    private static void solace$beginVillageJigsawDetection(
            RandomState randomState,
            int maxDepth,
            boolean useExpansionHack,
            ChunkGenerator chunkGenerator,
            StructureTemplateManager structureTemplateManager,
            LevelHeightAccessor heightAccessor,
            RandomSource random,
            Registry<StructureTemplatePool> templatePoolRegistry,
            PoolElementStructurePiece startPiece,
            List<PoolElementStructurePiece> pieces,
            VoxelShape free,
            CallbackInfo ci
    ) {
        JigsawVillageBuildingTool.begin(startPiece);
    }

    @Inject(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/RandomState;IZLnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Ljava/util/List;Lnet/minecraft/world/phys/shapes/VoxelShape;)V",
            at = @At("RETURN")
    )
    private static void solace$endVillageJigsawDetection(
            RandomState randomState,
            int maxDepth,
            boolean useExpansionHack,
            ChunkGenerator chunkGenerator,
            StructureTemplateManager structureTemplateManager,
            LevelHeightAccessor heightAccessor,
            RandomSource random,
            Registry<StructureTemplatePool> templatePoolRegistry,
            PoolElementStructurePiece startPiece,
            List<PoolElementStructurePiece> pieces,
            VoxelShape free,
            CallbackInfo ci
    ) {
        JigsawVillageBuildingTool.end();
    }
}
