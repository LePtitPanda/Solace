package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import de.keksuccino.modernworldcreation.util.rendering.gui.widgets.ArrowButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ArrowButton.class, priority = 1500, remap = false)
public abstract class ModernWorldCreationArrowButtonMixin {
    @Inject(method = "m_87963_", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$renderEnhancementArrow(GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof CreateWorldScreen) {
            ArrowButton button = (ArrowButton) (Object) this;
            boolean pointsLeft = button.getDirection() == ArrowButton.ArrowDirection.LEFT;
            WorldCreationThemeRenderer.renderArrow((AbstractWidget) (Object) this, graphics, pointsLeft);
            ci.cancel();
        }
    }
}
