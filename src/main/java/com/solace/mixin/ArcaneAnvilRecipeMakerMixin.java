package com.solace.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "io.redspace.ironsspellbooks.jei.ArcaneAnvilRecipeMaker", remap = false)
public class ArcaneAnvilRecipeMakerMixin {
    @Inject(method = "getRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private static void solace$hideDisabledArcaneAnvilRecipes(CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(List.of());
    }
}
