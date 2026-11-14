package com.solace.mixin;

import com.solace.quests.locks.RecipeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recipe locks for the Sophisticated crafting upgrade (backpacks and storage). It keeps its last recipe and
 * lets the player cycle through alternatives without asking vanilla again, so the result is checked after
 * both: a locked recipe's result is taken back out.
 */
@Mixin(value = CraftingUpgradeContainer.class, remap = false)
public abstract class BackpackCraftingRecipeLockMixin {
    @Shadow @Final private ResultContainer craftResult;
    @Shadow @Final private ResultSlot craftingResultSlot;
    @Shadow private CraftingRecipe lastRecipe;

    @Inject(method = {"updateCraftingResult", "selectCraftingResult"}, at = @At("TAIL"))
    private void solace$recipeLock(CallbackInfo callback) {
        if (!(((UpgradeContainerBase<?, ?>) (Object) this).getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (RecipeLocks.locked(player, craftResult.getRecipeUsed()) || RecipeLocks.locked(player, lastRecipe)) {
            lastRecipe = null;
            craftResult.setRecipeUsed(null);
            craftingResultSlot.set(ItemStack.EMPTY);
        }
    }
}
