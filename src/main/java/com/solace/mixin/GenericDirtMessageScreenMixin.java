package com.solace.mixin;

import com.solace.client.gui.EnhancementLoadingScreenRenderer;
import com.solace.client.loading.DimensionLoadingScreenRenderer;
import com.solace.config.DimensionLoadingScreenConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GenericDirtMessageScreen.class, priority = 1600)
public abstract class GenericDirtMessageScreenMixin extends Screen {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    protected GenericDirtMessageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void solace$renderDimensionLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                      float partialTick, CallbackInfo ci) {
        if (this.title.getContents() instanceof TranslatableContents translatable
                && "createWorld.preparing".equals(translatable.getKey())) {
            EnhancementLoadingScreenRenderer.render((Screen) (Object) this, graphics, this.title);
            ci.cancel();
            return;
        }
        DimensionLoadingScreenConfig.Entry config = DimensionLoadingScreenConfig.get(OVERWORLD);
        if (DimensionLoadingScreenRenderer.render((Screen) (Object) this, graphics, config, null, this.title)) {
            ci.cancel();
        }
    }
}
