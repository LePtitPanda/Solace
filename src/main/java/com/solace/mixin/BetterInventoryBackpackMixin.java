package com.solace.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Better Inventory and Backpacks: a backpack is never placed as a block and right-clicking it does not open it;
 * it is only used from the inventory. SRG method names, since the target is not remapped.
 */
@Mixin(targets = "dev.fallingcloud.betterinventory.item.BackpackItem", remap = false)
public abstract class BetterInventoryBackpackMixin {
    @Inject(method = "m_6225_", at = @At("HEAD"), cancellable = true, require = 0)
    private void solace$noPlacing(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "m_7203_", at = @At("HEAD"), cancellable = true, require = 0)
    private void solace$noOpening(Level level, Player player, InteractionHand hand,
                                  CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        cir.setReturnValue(InteractionResultHolder.pass(player.getItemInHand(hand)));
    }
}
