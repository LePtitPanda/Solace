package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "org.embeddedt.embeddium.gui.frame.AbstractFrame", priority = 1500, remap = false)
public abstract class EmbeddiumFrameMixin {
    @ModifyConstant(method = "m_88315_", constant = @Constant(intValue = -5592406), remap = false)
    private int solace$useGoldFrame(int original) {
        return WorldCreationThemeRenderer.isEmbeddiumVideoScreen(Minecraft.getInstance().screen)
                ? WorldCreationThemeRenderer.LINE
                : original;
    }
}
