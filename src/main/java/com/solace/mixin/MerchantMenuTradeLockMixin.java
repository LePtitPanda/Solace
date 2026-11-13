package com.solace.mixin;

import com.solace.quests.locks.TradeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Trade locks: with hidden offers the client's indices are shifted; map them back on the server. */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuTradeLockMixin {
    @Shadow
    @Final
    private Merchant trader;

    @ModifyVariable(method = "setSelectionHint", at = @At("HEAD"), argsOnly = true)
    private int solace$selectionIndex(int index) {
        return solace$map(index);
    }

    @ModifyVariable(method = "tryMoveItems", at = @At("HEAD"), argsOnly = true)
    private int solace$moveIndex(int index) {
        return solace$map(index);
    }

    private int solace$map(int index) {
        // The client's own menu has a ClientSideMerchant whose player is not a ServerPlayer.
        return trader.getTradingPlayer() instanceof ServerPlayer player ? TradeLocks.serverIndex(player, index) : index;
    }
}
