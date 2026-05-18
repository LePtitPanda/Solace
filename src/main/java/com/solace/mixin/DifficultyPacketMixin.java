package com.solace.mixin;

import com.misanthropy.linggango.difficulty_enhancement.LinggangoEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.misanthropy.linggango.difficulty_enhancement.LinggangoEvents$DifficultyPacket", remap = false)
public abstract class DifficultyPacketMixin {
    @Mutable
    @Shadow
    @Final
    private String diffId;

    @Inject(method = "handleServerSide", at = @At("HEAD"), remap = false)
    private static void solace$forceExtremeOnHardcore(LinggangoEvents.DifficultyPacket packet, ServerPlayer player, CallbackInfo ci) {
        if (player != null && player.serverLevel().getLevelData().isHardcore()) {
            ((DifficultyPacketMixin) (Object) packet).diffId = "extreme";
        }
    }
}
