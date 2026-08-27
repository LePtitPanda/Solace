package com.solace.mixin;

import com.solace.client.gui.EmbeddiumThemeRenderer;
import com.solace.client.gui.WorldCreationThemeRenderer;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.jellysquid.mods.sodium.client.gui.options.control.ControlElement",
        priority = 1500, remap = false)
public abstract class EmbeddiumControlElementMixin {
    @Shadow(remap = false)
    private FlatButtonWidget.Style style;

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void solace$applyEnhancementStyle(GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        if (WorldCreationThemeRenderer.isEmbeddiumVideoScreen(Minecraft.getInstance().screen)) {
            this.style = EmbeddiumThemeRenderer.style();
        }
    }
}
