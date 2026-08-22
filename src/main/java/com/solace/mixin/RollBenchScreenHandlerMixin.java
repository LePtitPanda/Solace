package com.solace.mixin;

import com.solace.world.EnchantingReworkSupport;
import java.util.Optional;
import net.backupcup.mcde.util.EnchantmentSlots;
import net.backupcup.mcde.util.SlotPosition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.backupcup.mcde.screen.handler.RollBenchScreenHandler", remap = false)
public class RollBenchScreenHandlerMixin {
    @Inject(method = {"stillValid", "m_6875_", "canUse"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$stillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(EnchantingReworkSupport.stillValidAtEnchantingOrAtoningTable(this, player));
    }

    @Inject(method = {"method_7604", "m_6366_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$handleBookshelfClick(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (EnchantingReworkSupport.isBookshelfHandler(this)) {
            cir.setReturnValue(EnchantingReworkSupport.handleBookshelfClick(this, player, buttonId));
        }
    }

    @Inject(method = "canReroll", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$allowBookshelfAction(Player player, ResourceLocation enchantmentId, EnchantmentSlots slots,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (EnchantingReworkSupport.isBookshelfHandler(this)) {
            cir.setReturnValue(EnchantingReworkSupport.canPayForBookshelfAction(this, player));
        }
    }

    @Inject(method = "generateEnchantment", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$getBookshelfSlotAction(Player player, SlotPosition clickedSlot,
                                                CallbackInfoReturnable<Optional<?>> cir) {
        if (EnchantingReworkSupport.isBookshelfHandler(this)) {
            cir.setReturnValue(EnchantingReworkSupport.bookshelfSlotAction(this, clickedSlot));
        }
    }
}
