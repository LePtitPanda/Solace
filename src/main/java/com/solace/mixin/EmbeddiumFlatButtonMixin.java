package com.solace.mixin;

import com.solace.client.gui.EmbeddiumThemeRenderer;
import com.solace.client.gui.WorldCreationThemeRenderer;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FlatButtonWidget.class, priority = 1500, remap = false)
public abstract class EmbeddiumFlatButtonMixin {
    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void solace$applyEnhancementStyle(GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        if (WorldCreationThemeRenderer.isEmbeddiumVideoScreen(Minecraft.getInstance().screen)) {
            ((FlatButtonWidget) (Object) this).setStyle(EmbeddiumThemeRenderer.style());
        }
    }

    @ModifyConstant(method = "m_88315_", constant = @Constant(intValue = -3179338), remap = false)
    private int solace$useGoldSelectionIndicator(int original) {
        return WorldCreationThemeRenderer.isEmbeddiumVideoScreen(Minecraft.getInstance().screen)
                ? WorldCreationThemeRenderer.GOLD
                : original;
    }
}
