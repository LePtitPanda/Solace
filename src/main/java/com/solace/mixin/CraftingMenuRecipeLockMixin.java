package com.solace.mixin;

import com.solace.quests.locks.RecipeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Recipe locks: a recipe locked for this player gives no result in a vanilla crafting grid (table and inventory). */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuRecipeLockMixin {
    @Redirect(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;setRecipeUsed(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/Recipe;)Z"))
    private static boolean solace$recipeLock(ResultContainer result, Level level, ServerPlayer player, Recipe<?> recipe) {
        return !RecipeLocks.locked(player, recipe) && result.setRecipeUsed(level, player, recipe);
    }
}
