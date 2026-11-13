package com.solace.mixin;

import com.solace.quests.locks.TradeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trade locks: the server never fills the result slot for a locked offer, whatever the client shows or sends. */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerTradeLockMixin {
    @Shadow
    @Final
    private Merchant merchant;
    @Shadow
    private MerchantOffer activeOffer;

    @Shadow
    public abstract void setItem(int slot, ItemStack stack);

    @Inject(method = "updateSellItem", at = @At("TAIL"))
    private void solace$tradeLocks(CallbackInfo callback) {
        if (activeOffer != null && merchant.getTradingPlayer() instanceof ServerPlayer player
                && TradeLocks.locked(player, merchant, activeOffer)) {
            activeOffer = null;
            setItem(2, ItemStack.EMPTY);
        }
    }
}
