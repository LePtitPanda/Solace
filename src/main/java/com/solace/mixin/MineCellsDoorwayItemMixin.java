package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import com.github.mim1q.minecells.dimension.MineCellsDimension;
import com.github.mim1q.minecells.item.DoorwayItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DoorwayItem.class, remap = false)
public abstract class MineCellsDoorwayItemMixin {
    @Redirect(
            method = {
                    "m_40610_",
                    "m_6883_"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/mim1q/minecells/dimension/MineCellsDimension;of(Lnet/minecraft/world/level/Level;)Lcom/github/mim1q/minecells/dimension/MineCellsDimension;",
                    remap = false
            ),
            remap = false
    )
    private MineCellsDimension solace$allowDoorwayItemInUndergarden(@Coerce Object world) {
        return MineCellsDoorwayCompat.strictSourceDimensionFor(world);
    }

    @Redirect(
            method = "m_6883_",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/github/mim1q/minecells/dimension/MineCellsDimension;translationKey:Ljava/lang/String;",
                    remap = false
            ),
            remap = false
    )
    private String solace$describeHubAsUndergarden(MineCellsDimension dimension) {
        return MineCellsDoorwayCompat.translationKeyFor(dimension);
    }
}
