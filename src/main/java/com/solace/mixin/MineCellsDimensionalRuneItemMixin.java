package com.solace.mixin;

import com.github.mim1q.minecells.item.DimensionalRuneItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DimensionalRuneItem.class, remap = false)
public abstract class MineCellsDimensionalRuneItemMixin {
    @Redirect(
            method = "m_6225_",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z",
                    remap = false
            ),
            remap = false
    )
    private boolean solace$allowRuneEditingInUndergarden(String dimensionPath, Object expectedPath) {
        return "undergarden".equals(dimensionPath);
    }
}
