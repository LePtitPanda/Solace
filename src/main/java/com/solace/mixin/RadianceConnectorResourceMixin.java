package com.solace.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the early GeckoLib listeners and adds Radiance's auxiliary loader. */
@Mixin(Minecraft.class)
public abstract class RadianceConnectorResourceMixin {
    @Shadow @Final private ReloadableResourceManager resourceManager;

    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;",
            shift = At.Shift.AFTER, remap = false))
    private void solace$registerRadianceOnEarlyManager(GameConfig config, CallbackInfo ci) {
        if (resourceManager == null) {
            throw new IllegalStateException("Connector did not create its early resource manager");
        }
        try {
            PreparableReloadListener listener = (PreparableReloadListener) Class.forName(
                    "com.radiance.client.texture.AuxiliaryTextureReloader")
                    .getDeclaredConstructor().newInstance();
            resourceManager.registerReloadListener(listener);
            LogManager.getLogger("Solace/RadianceCompat").info(
                    "Registered Radiance auxiliary texture reloader on Connector's resource manager");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register Radiance's auxiliary texture reloader", exception);
        }
    }
}
