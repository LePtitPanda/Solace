package com.solace.mixin;

import com.solace.client.loading.DimensionLoadingScreenRenderer;
import com.solace.config.DimensionLoadingScreenConfig;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelLoadingScreen.class, priority = 1600)
public abstract class LevelLoadingScreenMixin extends Screen {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @Shadow
    @Final
    private StoringChunkProgressListener progressListener;

    protected LevelLoadingScreenMixin() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void solace$renderDimensionLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                      float partialTick, CallbackInfo ci) {
        DimensionLoadingScreenConfig.Entry config = DimensionLoadingScreenConfig.get(OVERWORLD);
        int progress = Mth.clamp(this.progressListener.getProgress(), 0, 100);
        if (DimensionLoadingScreenRenderer.render((Screen) (Object) this, graphics, config, progress, null)) {
            ci.cancel();
        }
    }
}
