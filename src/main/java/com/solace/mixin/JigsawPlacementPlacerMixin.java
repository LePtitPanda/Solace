package com.solace.mixin;

import com.solace.world.JigsawVillageBuildingTool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer", priority = 1100)
public abstract class JigsawPlacementPlacerMixin {
    @Redirect(
            method = "tryPlacingChildren",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z"
            )
    )
    private boolean solace$filterVillageCandidatePool(
            List<StructurePoolElement> destination,
            Collection<? extends StructurePoolElement> candidates
    ) {
        return destination.addAll(JigsawVillageBuildingTool.filterCandidates(new ArrayList<>(candidates)));
    }
}
