package com.solace.mixin;

import com.solace.world.JigsawVillageBuildingTool;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureTemplatePool.class)
public abstract class StructureTemplatePoolMixin {
    @Shadow
    @Final
    private ObjectArrayList<StructurePoolElement> templates;

    @Inject(method = "getRandomTemplate", at = @At("RETURN"), cancellable = true)
    private void solace$filterVillageJigsawRandomCandidate(RandomSource random, CallbackInfoReturnable<StructurePoolElement> cir) {
        cir.setReturnValue(JigsawVillageBuildingTool.filterRandomCandidate(cir.getReturnValue(), this.templates));
    }
}
