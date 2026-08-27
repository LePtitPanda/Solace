package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "org.embeddedt.embeddium.gui.EmbeddiumVideoOptionsScreen", priority = 1500, remap = false)
public abstract class EmbeddiumVideoOptionsScreenMixin {
    @ModifyConstant(method = "m_280273_", constant = @Constant(intValue = -3179338), remap = false)
    private int solace$useGoldLogoTint(int original) {
        return WorldCreationThemeRenderer.GOLD;
    }
}
