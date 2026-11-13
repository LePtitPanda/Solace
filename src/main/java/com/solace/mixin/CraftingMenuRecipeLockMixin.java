package com.solace.mixin;

import com.solace.quests.locks.RecipeLocks;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recipe locks: a recipe locked for this player gives no result in a vanilla crafting grid (table and
 * inventory). Runs after the method, so mods that redirect the recipe lookup inside it (Polymorph) still work.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuRecipeLockMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void solace$recipeLock(AbstractContainerMenu menu, Level level, Player player,
                                          CraftingContainer grid, ResultContainer result, CallbackInfo callback) {
        if (player instanceof ServerPlayer server && !result.getItem(0).isEmpty()
                && RecipeLocks.locked(server, result.getRecipeUsed())) {
            result.setItem(0, ItemStack.EMPTY);
            result.setRecipeUsed(null);
            menu.setRemoteSlot(0, ItemStack.EMPTY);
            server.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0,
                    ItemStack.EMPTY));
        }
    }
}
