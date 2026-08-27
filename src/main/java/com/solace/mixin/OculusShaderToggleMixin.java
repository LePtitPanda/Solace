package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.element.ShaderPackSelectionList$TopButtonRowEntry", remap = false)
public abstract class OculusShaderToggleMixin {
    @Shadow(remap = false)
    public boolean allowEnableShadersButton;

    @Shadow(remap = false)
    public boolean shadersEnabled;

    @Unique
    private long solace$lastFrame;

    @Unique
    private float solace$hover;

    @Inject(method = "m_6311_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$renderThemedShaderToggle(GuiGraphics graphics, int index, int top, int left,
                                                 int width, int height, int mouseX, int mouseY,
                                                 boolean hovered, float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        long elapsed = this.solace$lastFrame == 0L ? 16L : Math.min(50L, now - this.solace$lastFrame);
        this.solace$lastFrame = now;
        float target = hovered ? 1.0F : 0.0F;
        float blend = 1.0F - (float) Math.exp(-elapsed / 85.0F);
        this.solace$hover += (target - this.solace$hover) * blend;

        Component label;
        if (!this.allowEnableShadersButton) {
            label = Component.translatable("options.iris.shaders.nonePresent");
        } else if (this.shadersEnabled) {
            label = Component.translatable("options.iris.shaders.enabled");
        } else {
            label = Component.translatable("options.iris.shaders.disabled");
        }

        WorldCreationThemeRenderer.renderOculusShaderToggle(
                graphics, left, top, width, height, label, this.solace$hover);
        ci.cancel();
    }
}
