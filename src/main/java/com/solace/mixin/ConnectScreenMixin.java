package com.solace.mixin;

import com.solace.client.loading.DimensionLoadingScreenRenderer;
import com.solace.config.DimensionLoadingScreenConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConnectScreen.class, priority = 1600)
public abstract class ConnectScreenMixin extends Screen {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @Shadow
    private Component status;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void solace$renderJoiningWorldScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                 float partialTick, CallbackInfo ci) {
        if (!(this.status.getContents() instanceof TranslatableContents translatable)
                || !"connect.joining".equals(translatable.getKey())) {
            return;
        }

        DimensionLoadingScreenConfig.Entry config = DimensionLoadingScreenConfig.get(OVERWORLD);
        if (DimensionLoadingScreenRenderer.render((Screen) (Object) this, graphics, config, null, this.status)) {
            super.render(graphics, mouseX, mouseY, partialTick);
            ci.cancel();
        }
    }
}
