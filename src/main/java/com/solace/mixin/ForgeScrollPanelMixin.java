package com.solace.mixin;

import com.mojang.blaze3d.vertex.Tesselator;
import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = net.minecraftforge.client.gui.widget.ScrollPanel.class, priority = 1500, remap = false)
public abstract class ForgeScrollPanelMixin {
    @Mutable
    @Shadow
    @Final
    protected int height;

    @Mutable
    @Shadow
    @Final
    protected int top;

    @Mutable
    @Shadow
    @Final
    protected int bottom;

    @Shadow
    @Final
    protected int left;

    @Shadow
    @Final
    protected int right;

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void solace$keepForgePanelBelowMenuHeader(GuiGraphics graphics, int mouseX, int mouseY,
                                                       float partialTick, CallbackInfo ci) {
        if (WorldCreationThemeRenderer.isFrontEndScreen(Minecraft.getInstance().screen)
                && Minecraft.getInstance().screen.getClass().getName().equals("net.minecraftforge.client.gui.ModListScreen")) {
            if (this.top < WorldCreationThemeRenderer.MENU_CONTENT_TOP) {
                this.top = WorldCreationThemeRenderer.MENU_CONTENT_TOP;
            }
            this.bottom = Math.min(this.bottom,
                    WorldCreationThemeRenderer.getFooterY(Minecraft.getInstance().screen));
            this.height = Math.max(20, this.bottom - this.top);
        }
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$renderEnhancementPanel(GuiGraphics graphics, Tesselator tessellator,
                                                float partialTick, CallbackInfo ci) {
        if (WorldCreationThemeRenderer.isFrontEndScreen(Minecraft.getInstance().screen)
                && Minecraft.getInstance().screen.getClass().getName().equals("net.minecraftforge.client.gui.ModListScreen")) {
            graphics.fill(this.left, this.top, this.right, this.bottom, 0xD90A0A0A);
            WorldCreationThemeRenderer.renderBracketPanel(graphics, this.left, this.top,
                    this.right - this.left, this.bottom - this.top, WorldCreationThemeRenderer.LINE);
            ci.cancel();
        }
    }
}
