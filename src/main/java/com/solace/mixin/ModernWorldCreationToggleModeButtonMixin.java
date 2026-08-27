package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import de.keksuccino.modernworldcreation.util.rendering.gui.widgets.ToggleModeButton;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ToggleModeButton.class, priority = 1500, remap = false)
public abstract class ModernWorldCreationToggleModeButtonMixin {
    @Shadow
    protected boolean selected;

    @Shadow
    protected int animationTicker;

    @Shadow
    protected int addToHeightWhenHovered;

    @Shadow
    protected Color labelBackgroundColor;

    @Shadow
    protected Color borderColor;

    @Shadow
    protected boolean darkenWhenUnfocused;

    @Inject(method = "_renderWidget", at = @At("HEAD"), remap = false)
    private void solace$keepModeImageCardStable(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof CreateWorldScreen) {
            this.animationTicker = 0;
            this.addToHeightWhenHovered = 0;
            this.labelBackgroundColor = this.selected
                    ? new Color(78, 57, 22, 235)
                    : new Color(7, 7, 15, 220);
            this.borderColor = this.selected
                    ? new Color(212, 168, 67, 255)
                    : new Color(106, 78, 24, 255);
            this.darkenWhenUnfocused = true;
        }
    }

    @Inject(method = "_renderWidget", at = @At("TAIL"), remap = false)
    private void solace$renderEnhancementModeButton(GuiGraphics graphics, int mouseX, int mouseY,
                                                    float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof CreateWorldScreen) {
            WorldCreationThemeRenderer.renderModeButtonOverlay((AbstractWidget) (Object) this, graphics, this.selected);
        }
    }
}
