package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Embeddium inlines DefaultColors.ELEMENT_ACTIVATED (pink, 0xFFCF7CB6) into its
 * widgets: enabled tick boxes, selected tabs and highlighted rows. Swap it for the
 * Astralis gold so Video Settings matches the rest of the UI.
 */
@Mixin(targets = {
        "me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl$TickBoxControlElement",
        "me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget",
        "org.embeddedt.embeddium.gui.EmbeddiumVideoOptionsScreen",
        "org.embeddedt.embeddium.gui.frame.OptionPageFrame"
}, remap = false)
public abstract class EmbeddiumAccentColorMixin {
    @ModifyConstant(method = "*", constant = @Constant(intValue = 0xFFCF7CB6), require = 0, remap = false)
    private int solace$goldAccent(int original) {
        return WorldCreationThemeRenderer.GOLD;
    }
}
