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
    private static final String SOLACE$KEEP_INVENTORY_DIFFICULTY = "cozy";

    @Mutable
    @Shadow
    @Final
    private String diffId;

    @Mutable
    @Shadow
    @Final
    private boolean keepInventory;

    @Inject(method = "handleServerSide", at = @At("HEAD"), remap = false)
    private static void solace$sanitizeDifficultyChoice(LinggangoEvents.DifficultyPacket packet, ServerPlayer player, CallbackInfo ci) {
        DifficultyPacketMixin self = (DifficultyPacketMixin) (Object) packet;
        if (player != null && player.serverLevel().getLevelData().isHardcore()) {
            self.diffId = "extreme";
        }
        // Keep Inventory is a Cozy-only perk; the packet applies the gamerule straight
        // from the client's flag, so enforce it here as well as in the selection UI.
        if (!SOLACE$KEEP_INVENTORY_DIFFICULTY.equals(self.diffId)) {
            self.keepInventory = false;
        }
    }
}
