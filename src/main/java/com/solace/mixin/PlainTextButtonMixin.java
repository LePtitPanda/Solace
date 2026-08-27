package com.solace.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlainTextButton.class)
public abstract class PlainTextButtonMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void solace$hideVanillaTitleCopyright(GuiGraphics graphics, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo ci) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        if (Minecraft.getInstance().screen instanceof TitleScreen
                && widget.getMessage().getString().equals(TitleScreen.COPYRIGHT_TEXT.getString())) {
            ci.cancel();
        }
    }
}
