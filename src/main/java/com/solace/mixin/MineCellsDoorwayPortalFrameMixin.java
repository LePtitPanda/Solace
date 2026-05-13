package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import com.github.mim1q.minecells.block.portal.DoorwayPortalBlock;
import com.github.mim1q.minecells.dimension.MineCellsDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DoorwayPortalBlock.Frame.class, remap = false)
public abstract class MineCellsDoorwayPortalFrameMixin {
    @Redirect(
            method = "m_6861_",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/mim1q/minecells/dimension/MineCellsDimension;of(Lnet/minecraft/world/level/Level;)Lcom/github/mim1q/minecells/dimension/MineCellsDimension;",
                    remap = false
            ),
            remap = false
    )
    private MineCellsDimension solace$validateFrameInUndergarden(@Coerce Object world) {
        return MineCellsDoorwayCompat.strictSourceDimensionFor(world);
    }
}
