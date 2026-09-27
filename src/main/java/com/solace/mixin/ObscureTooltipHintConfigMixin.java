package com.solace.mixin;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
public abstract class ObscureTooltipHintConfigMixin {
    @Shadow private ForgeConfigSpec spec;
    @Shadow @Final private List<String> path;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void solace$deferUnloadedTooltipHint(CallbackInfoReturnable<Object> callback) {
        // The hint immediately writes/saves its flag; a default value is unsafe before config loading.
        if ((spec == null || !spec.isLoaded()) && !path.isEmpty()
                && "showVibrantTooltipsHint".equals(path.get(path.size() - 1))) {
            callback.setReturnValue(Boolean.FALSE);
        }
    }
}
