package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The main menu has its own dark backdrop (no lattice). It is drawn over the
 * panorama and its overlay rather than replacing the panorama call, because Wither Storm
 * already redirects that call and only one redirect can win. FancyMenu draws its own
 * background over this only while its layout enables one.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenBackdropMixin {
    // Optional: if another mod moves this blit, the menu keeps its panorama instead of crashing.
    @Inject(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V",
            ordinal = 0, shift = At.Shift.AFTER))
    private void solace$drawBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        // The fade-in may have left a transparent shader color behind; the backdrop is opaque.
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        WorldCreationThemeRenderer.renderTitleBackdrop((Screen) (Object) this, graphics);
    }
}
