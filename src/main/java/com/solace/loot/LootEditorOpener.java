package com.solace.loot;

import com.solace.menu.LootEditorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

public final class LootEditorOpener {
    private static final String REMEMBERED_TABLE_KEY = "SolaceLootEditorSource";

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
        open(player, pos, table, 0, page);
        return true;
    }

    public static boolean resetChest(ServerPlayer player, BlockPos pos, ResourceLocation source) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        ResourceLocation currentSource = lootTable(blockEntity);
        if (blockEntity == null || currentSource == null || !currentSource.equals(source)) {
            player.displayClientMessage(Component.literal("The targeted chest no longer matches this editor."), true);
            return false;
        }

        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            container.clearContent();
            container.setLootTable(source, 0L);
        } else {
            if (blockEntity instanceof Container container) {
                container.clearContent();
            }
            CompoundTag tag = blockEntity.saveWithoutMetadata();
            tag.remove("Items");
            tag.putString("LootTable", source.toString());
            tag.putLong("LootTableSeed", 0L);
            blockEntity.load(tag);
        }

        rememberSource(blockEntity, source);
        if (ModList.get().isLoaded("lootr")) {
            LootEditorLootrCompat.reset(blockEntity, player);
        }
        blockEntity.setChanged();
        BlockState state = player.level().getBlockState(pos);
        player.level().sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        player.displayClientMessage(Component.literal("Chest reset to unopened. Its next open will roll loot again."), true);
        return true;
    }

    public static void reopen(ServerPlayer player, BlockPos pos, ResourceLocation source, int pool, int page) {
        player.server.execute(() -> open(player, pos, source, pool, page));
    }

    private static void open(ServerPlayer player, BlockPos pos, ResourceLocation source, int pool, int page) {
        LootStructureIndex.ensureBuilding(player.server, player);
        LootEditorSnapshot snapshot = LootEditorManager.snapshot(player.server, pos, source, pool, page);
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
        if (tag.contains("LootTable", CompoundTag.TAG_STRING)) {
            ResourceLocation liveTable = ResourceLocation.tryParse(tag.getString("LootTable"));
            if (liveTable != null) {
                rememberSource(blockEntity, liveTable);
                return liveTable;
            }
        }
        return ResourceLocation.tryParse(blockEntity.getPersistentData().getString(REMEMBERED_TABLE_KEY));
    }

    private static void rememberSource(BlockEntity blockEntity, ResourceLocation source) {
        String value = source.toString();
        if (!value.equals(blockEntity.getPersistentData().getString(REMEMBERED_TABLE_KEY))) {
            blockEntity.getPersistentData().putString(REMEMBERED_TABLE_KEY, value);
            blockEntity.setChanged();
        }
    }
}
