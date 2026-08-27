package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractSliderButton.class, priority = 2000)
public abstract class AbstractSliderButtonMixin {
    @Shadow
    protected double value;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void solace$renderEnhancementSlider(GuiGraphics graphics, int mouseX, int mouseY,
                                                 float partialTick, CallbackInfo ci) {
        if (WorldCreationThemeRenderer.isFrontEndScreen(Minecraft.getInstance().screen)) {
            WorldCreationThemeRenderer.renderSlider((AbstractWidget) (Object) this, graphics, this.value);
            ci.cancel();
        }
    }
}
