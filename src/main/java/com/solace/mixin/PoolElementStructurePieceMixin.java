package com.solace.mixin;

import com.solace.world.JigsawVillageBuildingTool;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PoolElementStructurePiece.class)
public abstract class PoolElementStructurePieceMixin {
    @Inject(
            method = "<init>(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/level/block/Rotation;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)V",
            at = @At("RETURN")
    )
    private void solace$recordVillageJigsawPiece(
            StructureTemplateManager structureTemplateManager,
            StructurePoolElement element,
            BlockPos position,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox boundingBox,
            CallbackInfo ci
    ) {
        PoolElementStructurePiece piece = (PoolElementStructurePiece) (Object) this;
        JigsawVillageBuildingTool.recordPiece(piece.getElement(), position, boundingBox);
    }
}
