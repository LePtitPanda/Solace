package com.solace.mixin;

import com.solace.world.JigsawVillageBuildingTool;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.worldgen.lithostitched.worldgen.structure.LithostitchedTemplates", remap = false)
public abstract class LithostitchedTemplatesMixin {
    @Inject(method = "shuffle", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void solace$filterVillageCandidates(
            RandomSource random,
            CallbackInfoReturnable<List<StructurePoolElement>> cir
    ) {
        cir.setReturnValue(JigsawVillageBuildingTool.filterCandidates(new ArrayList<>(cir.getReturnValue())));
    }
}
