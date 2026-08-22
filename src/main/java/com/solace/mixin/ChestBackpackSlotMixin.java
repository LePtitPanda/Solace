package com.solace.mixin;

import com.solace.backpack.BackpackRestrictions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class ChestBackpackSlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void solace$keepFilledChestBackpackEquipped(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        if (slot.container instanceof Inventory && slot.getContainerSlot() == 38
                && BackpackRestrictions.hasStoredItems(slot.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
