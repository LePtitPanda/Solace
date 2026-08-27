package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.customization.element.elements.button.custombutton.ButtonElement",
        priority = 2000, remap = false)
public abstract class FancyMenuButtonElementMixin {
    @Shadow(remap = false)
    public abstract AbstractWidget getWidget();

    @Shadow(remap = false)
    public abstract boolean isSlider();

    @Inject(method = "renderElementWidget", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$renderEnhancementCustomButton(GuiGraphics graphics, int mouseX, int mouseY,
                                                       float partialTick, CallbackInfo ci) {
        AbstractWidget widget = this.getWidget();
        if (!this.isSlider()
                && widget != null
                && widget.getWidth() > 0
                && widget.getHeight() > 0
                && WorldCreationThemeRenderer.isThemedButtonScreen(Minecraft.getInstance().screen)) {
            boolean hovered = widget.isMouseOver(mouseX, mouseY) || widget.isFocused();
            WorldCreationThemeRenderer.renderButton(widget, graphics, false, hovered);
            ci.cancel();
        }
    }
}
