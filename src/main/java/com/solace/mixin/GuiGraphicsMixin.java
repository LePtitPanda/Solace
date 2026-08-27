package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
            at = @At("HEAD"), cancellable = true)
    private void solace$suppressDuplicateFrontEndTitle(Font font, Component text, int x, int y, int color,
                                                        CallbackInfo ci) {
        if (WorldCreationThemeRenderer.shouldSuppressVanillaTitle(Minecraft.getInstance().screen, text, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), cancellable = true)
    private void solace$suppressDuplicateFrontEndStringTitle(Font font, String text, int x, int y, int color,
                                                              CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && WorldCreationThemeRenderer.isFrontEndScreen(screen)
                && y < WorldCreationThemeRenderer.MENU_CONTENT_TOP
                && text.equals(screen.getTitle().getString())) {
            ci.cancel();
        }
    }
}
