package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.github.mim1q.minecells.world.state.MineCellsData$PlayerData$PortalData", remap = false)
public abstract class MineCellsDoorwayPortalDataMixin {
    @Inject(method = "writeNbt", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$writeNullSafeHubPortalData(@Coerce Object nbt, CallbackInfoReturnable<Object> cir) {
        MineCellsDoorwayCompat.writePortalDataNbt(this, nbt);
        cir.setReturnValue(nbt);
    }
}
