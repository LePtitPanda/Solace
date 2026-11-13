package com.solace.mixin;

import com.solace.quests.locks.TradeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Trade locks: every offer list a player is sent goes through {@link TradeLocks#view}. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTradeLockMixin {
    @ModifyVariable(method = "sendMerchantOffers", at = @At("HEAD"), argsOnly = true)
    private MerchantOffers solace$tradeLocks(MerchantOffers offers) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        return self.containerMenu instanceof MerchantMenu menu
                ? TradeLocks.view(self, ((MerchantMenuAccessor) menu).solace$getTrader(), offers) : offers;
    }
}
