package com.solace.mixin;

import com.solace.client.loading.DimensionLoadingScreenRenderer;
import com.solace.config.DimensionLoadingScreenConfig;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReceivingLevelScreen.class, priority = 1600)
public abstract class ReceivingLevelScreenMixin extends Screen {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final Component LOADING_TERRAIN = Component.translatable("multiplayer.downloadingTerrain");

    protected ReceivingLevelScreenMixin() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void solace$renderDimensionLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                      float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation dimension = minecraft.level == null ? OVERWORLD : minecraft.level.dimension().location();
        DimensionLoadingScreenConfig.Entry config = DimensionLoadingScreenConfig.get(dimension);
        if (DimensionLoadingScreenRenderer.render((Screen) (Object) this, graphics, config, null, LOADING_TERRAIN)) {
            ci.cancel();
        }
    }
}
