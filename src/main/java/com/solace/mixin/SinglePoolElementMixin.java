package com.solace.mixin;

import com.solace.world.JigsawVillageBuildingTool;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SinglePoolElement.class)
public abstract class SinglePoolElementMixin {
    @Inject(method = "getShuffledJigsawBlocks", at = @At("HEAD"))
    private void solace$enterVillageParentPlacement(
            StructureTemplateManager structureTemplateManager,
            BlockPos position,
            Rotation rotation,
            RandomSource random,
            CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir
    ) {
        if (!BlockPos.ZERO.equals(position)) {
            JigsawVillageBuildingTool.enterParentElement(
                    (StructurePoolElement) (Object) this,
                    position,
                    structureTemplateManager
            );
        }
    }
}
