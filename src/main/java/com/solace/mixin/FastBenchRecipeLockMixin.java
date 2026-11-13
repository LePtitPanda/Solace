package com.solace.mixin;

import com.solace.quests.locks.RecipeLocks;
import dev.shadowsoffire.fastbench.util.CraftingInventoryExt;
import dev.shadowsoffire.fastbench.util.FastBenchUtil;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recipe locks for FastWorkbench, which finds recipes itself and never asks vanilla about the player.
 * Runs after FastWorkbench (and anything that redirects its recipe lookup, e.g. Polymorph) has filled the
 * result: a locked recipe's result is taken back out and the client is told.
 */
@Mixin(value = FastBenchUtil.class, remap = false)
public abstract class FastBenchRecipeLockMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void solace$recipeLock(Level level, Player player, CraftingInventoryExt inventory,
                                          ResultContainer result, CallbackInfo callback) {
        if (player instanceof ServerPlayer polymorphPlayer) {
            com.solace.compat.PolymorphRecipeLocks.filter(polymorphPlayer);
        }
        if (player instanceof ServerPlayer server && !result.getItem(0).isEmpty()
                && RecipeLocks.locked(server, result.getRecipeUsed())) {
            result.setItem(0, ItemStack.EMPTY);
            result.setRecipeUsed(null);
            server.connection.send(new ClientboundContainerSetSlotPacket(server.containerMenu.containerId,
                    server.containerMenu.incrementStateId(), 0, ItemStack.EMPTY));
        }
    }
}
