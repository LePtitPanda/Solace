package com.solace.mixin;

import appeng.helpers.IMenuCraftingPacket;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.CraftingTermSlot;
import com.solace.quests.locks.RecipeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2 crafting terminal: shift-click and pull-from-network crafting look the recipe up again here. */
@Mixin(value = CraftingTermSlot.class, remap = false)
public abstract class Ae2CraftingTermSlotRecipeLockMixin {
    @Shadow @Final private IMenuCraftingPacket menu;

    @Inject(method = "findRecipe", at = @At("RETURN"), cancellable = true)
    private void solace$recipeLock(CraftingContainer grid, net.minecraft.world.level.Level level,
                                   CallbackInfoReturnable<Recipe<CraftingContainer>> callback) {
        if (menu instanceof AEBaseMenu base && base.getPlayer() instanceof ServerPlayer player
                && RecipeLocks.locked(player, callback.getReturnValue())) {
            callback.setReturnValue(null);
        }
    }
}
