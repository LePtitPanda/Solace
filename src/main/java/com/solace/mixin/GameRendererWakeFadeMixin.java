package com.solace.mixin;

import com.solace.client.WakeFadeClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the wake-from-dream black overlay after the frame's GUI, screen and toasts. Optional:
 * many mods reshape GameRenderer.render, and if this point is gone WakeFadeClient falls back
 * to Forge's screen and HUD events.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererWakeFadeMixin {
    @Inject(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", shift = At.Shift.AFTER))
    private void solace$wakeFade(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        WakeFadeClient.renderFromMixin();
    }
}
