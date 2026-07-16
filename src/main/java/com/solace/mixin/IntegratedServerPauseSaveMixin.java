package com.solace.mixin;

import com.solace.performance.ServerWorkScheduler;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Coalesces repeated saves caused by quickly opening and closing the pause menu.
 * Normal autosaves, explicit saves, world shutdown saves, and the first pause
 * save are untouched.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerPauseSaveMixin {
    @Redirect(
            method = "tickServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/server/IntegratedServer;saveEverything(ZZZ)Z"
            )
    )
    private boolean solace$coalescePauseSave(IntegratedServer server, boolean suppressLogs, boolean flush, boolean force) {
        if (!ServerWorkScheduler.shouldRunPauseSave(server)) {
            return false;
        }
        return server.saveEverything(suppressLogs, flush, force);
    }
}
