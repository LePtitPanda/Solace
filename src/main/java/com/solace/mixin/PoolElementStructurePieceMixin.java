package com.solace.mixin;

import com.solace.world.JigsawVillageBuildingTool;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(PoolElementStructurePiece.class)
public abstract class PoolElementStructurePieceMixin {
    /**
     * Templates whose ground line is above Y=0 (they carry an underground part).
     * The value is the template Y of the grade layer + 1, so the piece sinks that deep
     * and the beardifier flattens terrain at the grade instead of at the template bottom.
     */
    private static final Map<ResourceLocation, Integer> SOLACE$GROUND_LEVEL_DELTAS = Map.of(
            new ResourceLocation("minecraft", "village/plains/town_centers/plains_meeting_point_2"), 8
    );

    @Shadow @Mutable @Final private int groundLevelDelta;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/level/block/Rotation;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)V",
            at = @At("RETURN")
    )
    private void solace$recordAcceptedVillageJigsawPiece(
            StructureTemplateManager structureTemplateManager,
            StructurePoolElement element,
            BlockPos position,
            int groundLevelDelta,
            Rotation rotation,
            BoundingBox boundingBox,
            CallbackInfo ci
    ) {
        JigsawVillageBuildingTool.templateId(element)
                .map(SOLACE$GROUND_LEVEL_DELTAS::get)
                .ifPresent(delta -> this.groundLevelDelta = delta);
        JigsawVillageBuildingTool.recordAcceptedPiece(element, position, boundingBox);
    }
}
