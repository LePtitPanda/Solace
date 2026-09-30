package com.solace.menu;

import com.solace.loot.LootEditorEntry;
import com.solace.loot.LootEditorManager;
import com.solace.loot.LootEditorOpener;
import com.solace.loot.LootEditorSnapshot;
import com.solace.loot.LootExtraDetector;
import com.solace.loot.LootStructureIndex;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class LootEditorMenu extends AbstractContainerMenu {
    public static final int ENTRY_SLOTS = 27;
    public static final int ADD_SLOT = 27;
    public static final int PREVIOUS_PAGE = 1;
    public static final int NEXT_PAGE = 2;
    public static final int RESET = 3;
    public static final int REBUILD_INDEX = 4;
    public static final int TOGGLE_MODIFIERS = 5;
    public static final int RESET_CHEST = 6;
    public static final int PREVIOUS_POOL = 7;
    public static final int NEXT_POOL = 8;
    public static final int ADD_POOL = 9;
    public static final int ADD_GUARANTEED_POOL = 10;
    public static final int REMOVE_POOL = 11;
    public static final int REMOVE_ENTRY_BASE = 100;

    private final LootEditorSnapshot snapshot;
    private final List<LootEditorEntry> entries;
    private final SimpleContainer ghosts = new SimpleContainer(ENTRY_SLOTS + 1);

    public LootEditorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, LootEditorSnapshot.read(buffer));
    }

    public LootEditorMenu(int containerId, Inventory inventory, LootEditorSnapshot snapshot) {
        super(LootEditorRegistry.MENU, containerId);
        this.snapshot = snapshot;
        this.entries = new ArrayList<>(snapshot.entries());

        for (int i = 0; i < ENTRY_SLOTS; i++) {
            if (i < entries.size()) {
                ghosts.setItem(i, entries.get(i).stack().copy());
            }
            int x = 8 + (i % 9) * 24;
            int y = 72 + (i / 9) * 24;
            addSlot(new GhostSlot(ghosts, i, x, y));
        }
        addSlot(new GhostSlot(ghosts, ADD_SLOT, 224, 96));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 246 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 318));
        }
    }

    public LootEditorSnapshot snapshot() {
        return snapshot;
    }

    public LootEditorEntry entry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(snapshot.pos().getX() + 0.5D, snapshot.pos().getY() + 0.5D,
                snapshot.pos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || (!player.isCreative() && !player.hasPermissions(2))) {
            return false;
        }
        if (id == PREVIOUS_PAGE && snapshot.page() > 0) {
            reopen(serverPlayer, snapshot.selectedPool(), snapshot.page() - 1);
            return true;
        }
        if (id == NEXT_PAGE && snapshot.page() + 1 < snapshot.pageCount()) {
            reopen(serverPlayer, snapshot.selectedPool(), snapshot.page() + 1);
            return true;
        }
        if (id == PREVIOUS_POOL && snapshot.selectedPool() > 0) {
            reopen(serverPlayer, snapshot.selectedPool() - 1, 0);
            return true;
        }
        if (id == NEXT_POOL && snapshot.selectedPool() + 1 < snapshot.viewCount()) {
            reopen(serverPlayer, snapshot.selectedPool() + 1, 0);
            return true;
        }
        if (id == ADD_POOL) {
            int pool = LootEditorManager.addPool(serverPlayer.server, snapshot.source(), ItemStack.EMPTY);
            reopen(serverPlayer, pool, 0);
            return true;
        }
        if (id == ADD_GUARANTEED_POOL) {
            if (getCarried().isEmpty()) {
                serverPlayer.displayClientMessage(Component.literal("Carry an item stack first, then click Guaranteed +."), true);
                return false;
            }
            int pool = LootEditorManager.addPool(serverPlayer.server, snapshot.source(), getCarried().copy());
            reopen(serverPlayer, pool, 0);
            return true;
        }
        if (id == REMOVE_POOL && snapshot.selectedPool() < snapshot.pools().size()) {
            if (LootEditorManager.removePool(serverPlayer.server, snapshot.source(), snapshot.selectedPool())) {
                reopen(serverPlayer, Math.max(0, snapshot.selectedPool() - 1), 0);
                return true;
            }
        }
        if (id == RESET) {
            LootEditorManager.reset(serverPlayer.server, snapshot.source());
            reopen(serverPlayer, 0, 0);
            return true;
        }
        if (id == REBUILD_INDEX) {
            LootStructureIndex.rebuild(serverPlayer.server, serverPlayer);
            LootExtraDetector.start(serverPlayer.server, true, serverPlayer);
            return true;
        }
        if (id == TOGGLE_MODIFIERS) {
            LootEditorManager.toggleModifiers(serverPlayer.server, snapshot.source());
            reopen(serverPlayer, snapshot.selectedPool(), snapshot.page());
            return true;
        }
        if (id == RESET_CHEST) {
            return LootEditorOpener.resetChest(serverPlayer, snapshot.pos(), snapshot.source());
        }
        if (id >= REMOVE_ENTRY_BASE && id < REMOVE_ENTRY_BASE + ENTRY_SLOTS) {
            int slot = id - REMOVE_ENTRY_BASE;
            LootEditorEntry entry = entry(slot);
            if (entry == null) {
                return false;
            }
            ResourceLocation externalId = entry.external() ? ResourceLocation.tryParse(entry.externalItemId()) : null;
            boolean changed = entry.external()
                    ? externalId != null && LootEditorManager.setExtraChance(serverPlayer.server, snapshot.source(),
                    externalId, entry.stack(), 0)
                    : LootEditorManager.removeItem(serverPlayer.server, snapshot.source(), entry.path());
            if (changed) {
                reopen(serverPlayer, snapshot.selectedPool(), snapshot.page());
                return true;
            }
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId <= ADD_SLOT) {
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer && !getCarried().isEmpty()) {
                boolean changed = false;
                if (slotId == ADD_SLOT || slotId >= entries.size()) {
                    if (!snapshot.showingExtras()) {
                        int targetPool = snapshot.pools().isEmpty() ? 0 : snapshot.selectedPool();
                        changed = LootEditorManager.addItem(serverPlayer.server, snapshot.source(),
                                targetPool, getCarried().copy());
                    }
                } else if (entries.get(slotId).external()) {
                    LootEditorEntry entry = entries.get(slotId);
                    ResourceLocation externalId = ResourceLocation.tryParse(entry.externalItemId());
                    changed = externalId != null && LootEditorManager.replaceExtra(serverPlayer.server, snapshot.source(),
                            externalId, getCarried().copy(), Math.max(100, entry.chanceBasisPoints()));
                } else {
                    changed = LootEditorManager.replaceItem(serverPlayer.server, snapshot.source(),
                            entries.get(slotId).path(), getCarried().copy());
                }
                if (changed) {
                    reopen(serverPlayer, snapshot.selectedPool(), snapshot.page());
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (slotId > ADD_SLOT && slotId < slots.size() && !snapshot.showingExtras()) {
            ItemStack source = slots.get(slotId).getItem();
            if (!source.isEmpty() && player instanceof ServerPlayer serverPlayer) {
                int targetPool = snapshot.pools().isEmpty() ? 0 : snapshot.selectedPool();
                LootEditorManager.addItem(serverPlayer.server, snapshot.source(), targetPool, source.copy());
                reopen(serverPlayer, snapshot.selectedPool(), snapshot.page());
            }
        }
        return ItemStack.EMPTY;
    }

    private void reopen(ServerPlayer player, int pool, int page) {
        LootEditorOpener.reopen(player, snapshot.pos(), snapshot.source(), pool, page);
    }

    private static final class GhostSlot extends Slot {
        private GhostSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
