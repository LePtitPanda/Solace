package com.solace.mixin;

import com.solace.backpack.BackpackRestrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuBackpackMixin {
    private static final int CHEST_ARMOR_MENU_SLOT = 6;

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void solace$blockFilledBackpackQuickMove(Player player, int slotIndex,
                                                      CallbackInfoReturnable<ItemStack> cir) {
        InventoryMenu menu = (InventoryMenu) (Object) this;
        if (slotIndex == CHEST_ARMOR_MENU_SLOT
                && BackpackRestrictions.hasStoredItems(menu.getSlot(slotIndex).getItem())) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
