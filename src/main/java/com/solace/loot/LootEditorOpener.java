package com.solace.loot;

import com.solace.menu.LootEditorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

public final class LootEditorOpener {
    private LootEditorOpener() {
    }

    public static boolean openFromBlock(ServerPlayer player, BlockPos pos, int page) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        ResourceLocation table = lootTable(blockEntity);
        if (table == null) {
            player.displayClientMessage(Component.literal(
                    "This block has no unopened LootTable tag. Already-generated containers no longer retain their source table."), true);
            return false;
        }
        open(player, pos, table, page);
        return true;
    }

    public static void reopen(ServerPlayer player, BlockPos pos, ResourceLocation source, int page) {
        player.server.execute(() -> open(player, pos, source, page));
    }

    private static void open(ServerPlayer player, BlockPos pos, ResourceLocation source, int page) {
        LootStructureIndex.ensureBuilding(player.server, player);
        LootEditorSnapshot snapshot = LootEditorManager.snapshot(player.server, pos, source, page);
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Loot: " + source);
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player ignored) {
                return new LootEditorMenu(containerId, inventory, snapshot);
            }
        };
        NetworkHooks.openScreen(player, provider, snapshot::write);
    }

    @Nullable
    private static ResourceLocation lootTable(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        CompoundTag tag = blockEntity.saveWithoutMetadata();
        return tag.contains("LootTable", CompoundTag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("LootTable")) : null;
    }
}
