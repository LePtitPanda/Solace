package com.solace.mixin;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MI's Fabric entrypoint may initialize before Forge KubeJS becomes visible. */
@Pseudo
@Mixin(targets = "aztech.modern_industrialization.MIStartup", remap = false)
public abstract class ModernIndustrializationStartupMixin {
    @Shadow private static boolean initialized;

    @Inject(method = "onKubejsPluginLoaded", at = @At("HEAD"), cancellable = true, remap = false)
    private static void solace$reuseCompletedFabricInitialization(CallbackInfo ci) {
        if (initialized) {
            LogManager.getLogger("Solace/Create6Compat").info(
                    "MI already initialized through Connector; reusing it for Forge KubeJS startup");
            ci.cancel();
        }
    }
}
