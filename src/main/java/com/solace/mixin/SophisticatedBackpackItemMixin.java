package com.solace.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BackpackItem.class, remap = false)
public abstract class SophisticatedBackpackItemMixin {
    @Inject(method = "m_6225_", at = @At("HEAD"), cancellable = true)
    private void solace$disablePlacement(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "m_7203_", at = @At("HEAD"), cancellable = true)
    private void solace$disableHandOpening(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
    }

    @Inject(method = "m_142207_", at = @At("HEAD"), cancellable = true)
    private void solace$disableInventoryStash(ItemStack backpack, Slot slot, ClickAction action, Player player,
                                               CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "m_142305_", at = @At("HEAD"), cancellable = true)
    private void solace$disableCursorStash(ItemStack backpack, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
