package com.solace.menu;

import com.solace.loot.LootEditorEntry;
import com.solace.loot.LootEditorManager;
import com.solace.loot.LootEditorOpener;
import com.solace.loot.LootEditorSnapshot;
import com.solace.loot.LootStructureIndex;
import com.solace.loot.LootExtraDetector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
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
    public static final int DECREMENT_BASE = 100;
    public static final int INCREMENT_BASE = 200;
    public static final int REMOVE_BASE = 300;

    private final LootEditorSnapshot snapshot;
    private final List<LootEditorEntry> entries;
    private final SimpleContainer ghosts = new SimpleContainer(ENTRY_SLOTS + 1);
    private final List<DataSlot> weights = new ArrayList<>();
    private final Player player;

    public LootEditorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, LootEditorSnapshot.read(buffer));
    }

    public LootEditorMenu(int containerId, Inventory inventory, LootEditorSnapshot snapshot) {
        super(LootEditorRegistry.MENU, containerId);
        this.snapshot = snapshot;
        this.entries = new ArrayList<>(snapshot.entries());
        this.player = inventory.player;

        for (int i = 0; i < ENTRY_SLOTS; i++) {
            if (i < entries.size()) {
                ghosts.setItem(i, entries.get(i).stack().copy());
            }
            int x = 8 + (i % 9) * 24;
            int y = 40 + (i / 9) * 24;
            addSlot(new GhostSlot(ghosts, i, x, y));
            DataSlot weight = DataSlot.standalone();
            weight.set(i < entries.size() ? entries.get(i).weight() : 0);
            weights.add(weight);
            addDataSlot(weight);
        }
        addSlot(new GhostSlot(ghosts, ADD_SLOT, 220, 64));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 154 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 226));
        }
    }

    public LootEditorSnapshot snapshot() {
        return snapshot;
    }

    public LootEditorEntry entry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public int weight(int index) {
        return index >= 0 && index < weights.size() ? weights.get(index).get() : 0;
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
            LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page() - 1);
            return true;
        }
        if (id == NEXT_PAGE && snapshot.page() + 1 < snapshot.pageCount()) {
            LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page() + 1);
            return true;
        }
        if (id == RESET) {
            LootEditorManager.reset(serverPlayer.server, snapshot.source());
            LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), 0);
            return true;
        }
        if (id == REBUILD_INDEX) {
            LootStructureIndex.rebuild(serverPlayer.server, serverPlayer);
            LootExtraDetector.start(serverPlayer.server, true, serverPlayer);
            return true;
        }
        if (id == TOGGLE_MODIFIERS) {
            LootEditorManager.toggleModifiers(serverPlayer.server, snapshot.source());
            LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page());
            return true;
        }

        int slot = id % 100;
        LootEditorEntry entry = entry(slot);
        if (entry == null) {
            return false;
        }
        if (id >= DECREMENT_BASE && id < DECREMENT_BASE + ENTRY_SLOTS) {
            int step = entry.external() ? (player.isShiftKeyDown() ? 500 : 100) : (player.isShiftKeyDown() ? 10 : 1);
            int value = Math.max(entry.external() ? 0 : 1, weight(slot) - step);
            boolean changed = entry.external()
                    ? LootEditorManager.setExtraChance(serverPlayer.server, snapshot.source(),
                    new net.minecraft.resources.ResourceLocation(entry.externalItemId()), entry.stack(), value)
                    : LootEditorManager.setWeight(serverPlayer.server, snapshot.source(), entry.path(), value);
            if (changed) {
                weights.get(slot).set(value);
                if (entry.external()) {
                    LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page());
                } else {
                    broadcastChanges();
                }
                return true;
            }
        } else if (id >= INCREMENT_BASE && id < INCREMENT_BASE + ENTRY_SLOTS) {
            int step = entry.external() ? (player.isShiftKeyDown() ? 500 : 100) : (player.isShiftKeyDown() ? 10 : 1);
            int value = Math.min(entry.external() ? 10_000 : 100_000,
                    Math.max(0, weight(slot)) + step);
            boolean changed = entry.external()
                    ? LootEditorManager.setExtraChance(serverPlayer.server, snapshot.source(),
                    new net.minecraft.resources.ResourceLocation(entry.externalItemId()), entry.stack(), value)
                    : LootEditorManager.setWeight(serverPlayer.server, snapshot.source(), entry.path(), value);
            if (changed) {
                weights.get(slot).set(value);
                if (entry.external()) {
                    LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page());
                } else {
                    broadcastChanges();
                }
                return true;
            }
        } else if (id >= REMOVE_BASE && id < REMOVE_BASE + ENTRY_SLOTS) {
            boolean changed = entry.external()
                    ? LootEditorManager.setExtraChance(serverPlayer.server, snapshot.source(),
                    new net.minecraft.resources.ResourceLocation(entry.externalItemId()), entry.stack(), 0)
                    : LootEditorManager.removeItem(serverPlayer.server, snapshot.source(), entry.path());
            if (changed) {
                LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page());
                return true;
            }
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId <= ADD_SLOT) {
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer && !getCarried().isEmpty()) {
                boolean changed;
                if (slotId == ADD_SLOT || slotId >= entries.size()) {
                    changed = LootEditorManager.addItem(serverPlayer.server, snapshot.source(), getCarried().copy());
                } else if (entries.get(slotId).external()) {
                    LootEditorEntry entry = entries.get(slotId);
                    changed = LootEditorManager.replaceExtra(serverPlayer.server, snapshot.source(),
                            new net.minecraft.resources.ResourceLocation(entry.externalItemId()), getCarried().copy(),
                            Math.max(100, weight(slotId)));
                } else {
                    changed = LootEditorManager.replaceItem(serverPlayer.server, snapshot.source(),
                            entries.get(slotId).path(), getCarried().copy());
                }
                if (changed) {
                    LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page());
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (slotId > ADD_SLOT && slotId < slots.size()) {
            ItemStack source = slots.get(slotId).getItem();
            if (!source.isEmpty() && player instanceof ServerPlayer serverPlayer) {
                LootEditorManager.addItem(serverPlayer.server, snapshot.source(), source.copy());
                LootEditorOpener.reopen(serverPlayer, snapshot.pos(), snapshot.source(), snapshot.page());
            }
        }
        return ItemStack.EMPTY;
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
