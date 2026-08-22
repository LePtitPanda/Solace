package com.solace.mixin;

import com.solace.backpack.BackpackRestrictions;
import net.minecraft.server.level.ServerPlayer;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BackpackOpenMessage.class, remap = false)
public abstract class BackpackOpenMessageMixin {
    @Inject(method = "handleMessage", at = @At("HEAD"), cancellable = true)
    private static void solace$onlyOpenWornBackpack(ServerPlayer player, BackpackOpenMessage message, CallbackInfo ci) {
        if (player == null) {
            ci.cancel();
            return;
        }
        BackpackOpenMessageAccessor accessor = (BackpackOpenMessageAccessor) message;
        if (BackpackRestrictions.isAllowedEquippedContext(player, accessor.solace$getHandlerName(),
                accessor.solace$getIdentifier(), accessor.solace$getSlotIndex())) {
            return;
        }
        BackpackRestrictions.openEquippedBackpack(player);
        ci.cancel();
    }
}
