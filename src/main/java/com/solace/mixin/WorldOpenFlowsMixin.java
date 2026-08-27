package com.solace.mixin;

import com.solace.config.DimensionLoadingScreenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void solace$showReadingWorldScreen(Screen previousScreen, String levelName, CallbackInfo ci) {
        if (DimensionLoadingScreenConfig.get(OVERWORLD) != null) {
            this.minecraft.setScreen(new GenericDirtMessageScreen(Component.translatable("selectWorld.data_read")));
        }
    }
}
