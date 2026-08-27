package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void solace$prepareFrontEndLayout(GuiGraphics graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        WorldCreationThemeRenderer.prepareFrontEndLayout(screen);
        WorldCreationThemeRenderer.renderFrontEndChrome(screen, graphics);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void solace$renderWorldCreationBackground(GuiGraphics graphics, CallbackInfo ci) {
        if ((Object) this instanceof CreateWorldScreen screen) {
            WorldCreationThemeRenderer.renderWorldCreationBackground(screen, graphics);
            ci.cancel();
        } else if (WorldCreationThemeRenderer.isFrontEndScreen(Minecraft.getInstance().screen)) {
            WorldCreationThemeRenderer.renderFrontEndBackground((Screen) (Object) this, graphics);
            ci.cancel();
        }
    }

    @Inject(method = "renderDirtBackground", at = @At("HEAD"), cancellable = true)
    private void solace$replaceFrontEndDirt(GuiGraphics graphics, CallbackInfo ci) {
        if (WorldCreationThemeRenderer.isFrontEndScreen(Minecraft.getInstance().screen)) {
            WorldCreationThemeRenderer.renderFrontEndBackground((Screen) (Object) this, graphics);
            ci.cancel();
        }
    }
}
