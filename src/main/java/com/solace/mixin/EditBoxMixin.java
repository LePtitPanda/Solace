package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EditBox.class)
public abstract class EditBoxMixin {
    @Redirect(
            method = "renderWidget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V")
    )
    private void solace$renderWorldCreationEditBox(GuiGraphics graphics, int left, int top,
                                                    int right, int bottom, int color) {
        if (WorldCreationThemeRenderer.isFrontEndScreen(Minecraft.getInstance().screen)) {
            color = WorldCreationThemeRenderer.editBoxFillColor((EditBox) (Object) this, color);
        }
        graphics.fill(left, top, right, bottom, color);
    }
}
