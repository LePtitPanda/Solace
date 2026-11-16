package com.solace.mixin;

import com.solace.client.IntroHoldClient;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No advancement or recipe-unlock toasts at all, and no toast of any kind behind the first-join loading screen. */
@Mixin(ToastComponent.class)
public abstract class ToastComponentFilterMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void solace$filterToast(Toast toast, CallbackInfo ci) {
        if (toast instanceof AdvancementToast || toast instanceof RecipeToast || IntroHoldClient.muting()) {
            ci.cancel();
        }
    }
}
