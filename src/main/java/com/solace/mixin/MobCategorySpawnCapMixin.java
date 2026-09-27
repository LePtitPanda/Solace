package com.solace.mixin;

import com.solace.spawn.OverworldSpawnManager;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobCategory.class)
public abstract class MobCategorySpawnCapMixin {
    @Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
    private void solace$useAstralisOverworldCap(CallbackInfoReturnable<Integer> callbackInfo) {
        MobCategory category = (MobCategory) (Object) this;
        Integer cap = OverworldSpawnManager.categoryCap(category);
        if (cap != null) {
            callbackInfo.setReturnValue(cap);
        }
    }
}
