package com.solace.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {
    @Shadow
    @Final
    private DataSlot cost;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void solace$disableEnchantedBookApplication(CallbackInfo ci) {
        ItemCombinerMenuAccessor accessor = (ItemCombinerMenuAccessor) this;
        Container input = accessor.solace$getInputSlots();
        ItemStack left = input.getItem(0);
        ItemStack right = input.getItem(1);
        if (!left.isEmpty() && right.getItem() instanceof EnchantedBookItem) {
            accessor.solace$getResultSlots().setItem(0, ItemStack.EMPTY);
            cost.set(0);
            ci.cancel();
        }
    }
}
