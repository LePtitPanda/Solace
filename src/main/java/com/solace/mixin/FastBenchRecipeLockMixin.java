package com.solace.mixin;

import com.solace.quests.locks.RecipeLocks;
import dev.shadowsoffire.fastbench.util.CraftingInventoryExt;
import dev.shadowsoffire.fastbench.util.FastBenchUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Recipe locks for FastWorkbench, which finds recipes itself and never asks vanilla about the player. */
@Mixin(value = FastBenchUtil.class, remap = false)
public abstract class FastBenchRecipeLockMixin {
    @Redirect(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Ldev/shadowsoffire/fastbench/util/FastBenchUtil;findRecipe(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/item/crafting/Recipe;"))
    private static Recipe<CraftingContainer> solace$recipeLock(CraftingContainer grid, Level level, Level sameLevel,
                                                              Player player, CraftingInventoryExt inventory,
                                                              ResultContainer result) {
        Recipe<CraftingContainer> recipe = FastBenchUtil.findRecipe(grid, level);
        return player instanceof ServerPlayer server && RecipeLocks.locked(server, recipe) ? null : recipe;
    }
}
