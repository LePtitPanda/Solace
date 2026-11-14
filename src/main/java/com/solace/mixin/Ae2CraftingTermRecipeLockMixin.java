package com.solace.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.slot.CraftingTermSlot;
import com.solace.quests.locks.RecipeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recipe locks for the AE2 crafting terminal (wireless too), which finds its recipe without the player. */
@Mixin(value = CraftingTermMenu.class, remap = false)
public abstract class Ae2CraftingTermRecipeLockMixin {
    @Shadow private Recipe<CraftingContainer> currentRecipe;
    @Shadow @Final private CraftingTermSlot outputSlot;

    @Inject(method = "updateCurrentRecipeAndOutput", at = @At("TAIL"))
    private void solace$recipeLock(boolean force, CallbackInfo callback) {
        if (((AEBaseMenu) (Object) this).getPlayer() instanceof ServerPlayer player
                && RecipeLocks.locked(player, currentRecipe)) {
            currentRecipe = null;
            ((Slot) outputSlot).set(ItemStack.EMPTY);
        }
    }
}
