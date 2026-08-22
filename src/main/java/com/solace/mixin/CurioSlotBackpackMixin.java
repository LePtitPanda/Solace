package com.solace.mixin;

import com.solace.backpack.BackpackRestrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.common.inventory.CurioSlot;

@Mixin(value = CurioSlot.class, remap = false)
public abstract class CurioSlotBackpackMixin {
    @Inject(method = "m_150651_", at = @At("HEAD"), cancellable = true)
    private void solace$keepFilledBackpackEquipped(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = ((CurioSlot) (Object) this).getItem();
        if (BackpackRestrictions.hasStoredItems(stack)) {
            cir.setReturnValue(false);
        }
    }
}
