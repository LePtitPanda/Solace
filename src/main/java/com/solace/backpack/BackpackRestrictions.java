package com.solace.backpack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.minecraftforge.network.NetworkHooks;
import top.theillusivec4.curios.api.CuriosApi;

public final class BackpackRestrictions {
    private BackpackRestrictions() {
    }

    public static boolean isBackpack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BackpackItem;
    }

    public static boolean hasStoredItems(ItemStack stack) {
        if (!isBackpack(stack)) {
            return false;
        }
        return stack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance()).map(wrapper -> {
            InventoryHandler inventory = wrapper.getInventoryHandler();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty()) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    public static boolean isAllowedEquippedContext(Player player, String handlerName, String identifier, int slot) {
        if ("armor".equals(handlerName)) {
            return slot == 0 && isBackpack(player.getItemBySlot(EquipmentSlot.CHEST));
        }
        if (!"curios".equals(handlerName) || !"back".equals(identifier)) {
            return false;
        }
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(handler -> handler.getStacksHandler("back"))
                .map(handler -> slot >= 0 && slot < handler.getStacks().getSlots()
                        && isBackpack(handler.getStacks().getStackInSlot(slot)))
                .orElse(false);
    }

    public static void openEquippedBackpack(ServerPlayer player) {
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isPresent()) {
            var backHandler = curios.get().getStacksHandler("back");
            if (backHandler.isPresent()) {
                var stacks = backHandler.get().getStacks();
                for (int slot = 0; slot < stacks.getSlots(); slot++) {
                    if (isBackpack(stacks.getStackInSlot(slot))) {
                        openContext(player, new BackpackContext.Item("curios", "back", slot));
                        return;
                    }
                }
            }
        }

        if (isBackpack(player.getItemBySlot(EquipmentSlot.CHEST))) {
            openContext(player, new BackpackContext.Item("armor", "", 0));
        }
    }

    private static void openContext(ServerPlayer player, BackpackContext.Item context) {
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, ignored) -> new BackpackContainer(id, player, context),
                        context.getDisplayName(player)),
                context::toBuffer);
    }
}
