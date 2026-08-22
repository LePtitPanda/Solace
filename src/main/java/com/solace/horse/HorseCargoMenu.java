package com.solace.horse;

import com.solace.mixin.AbstractHorseInventoryAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HorseCargoMenu extends AbstractContainerMenu {
    public static final int HORSE_SLOT_COUNT = 2 + HorseCargoData.MAX_SACKS + HorseCargoData.MAX_CARGO_SLOTS;
    private final Horse horse;
    private final Container equipment;
    private final HorseCargoData.PersistentContainer sacks;
    private final HorseCargoData.PersistentContainer cargo;
    private Item cachedArmorItem;
    private int cachedInnateCapacity = -1;
    private int cachedTotalCapacity;

    public HorseCargoMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, findHorse(playerInventory, data.readVarInt()));
    }

    public HorseCargoMenu(int containerId, Inventory playerInventory, Horse horse) {
        super(HorseCargoRegistry.HORSE_CARGO_MENU, containerId);
        this.horse = horse;
        this.equipment = horse == null ? new SimpleContainer(2) : ((AbstractHorseInventoryAccessor) horse).solace$getHorseInventory();
        this.sacks = horse == null ? null : HorseCargoData.sacks(horse);
        this.cargo = horse == null ? null : HorseCargoData.cargo(horse);

        Container sackInventory = sacks == null ? new SimpleContainer(HorseCargoData.MAX_SACKS) : sacks;
        Container cargoInventory = cargo == null ? new SimpleContainer(HorseCargoData.MAX_CARGO_SLOTS) : cargo;
        equipment.startOpen(playerInventory.player);

        addSlot(new Slot(equipment, 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return horse != null && horse.isSaddleable() && stack.is(Items.SADDLE) && !hasItem();
            }

            @Override
            public boolean isActive() {
                return horse != null && horse.isSaddleable();
            }
        });
        addSlot(new Slot(equipment, 1, 8, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return horse != null && sacks != null && cargo != null && horse.isArmor(stack)
                        && HorseCargoData.canUseCapacity(sacks, cargo,
                        HorseCargoData.innateCapacity(horse) + HorseArmorStats.of(stack).sackCapacity());
            }

            @Override
            public boolean mayPickup(Player player) {
                return horse != null && sacks != null && cargo != null
                        && HorseCargoData.canUseCapacity(sacks, cargo, HorseCargoData.innateCapacity(horse));
            }

            @Override
            public boolean isActive() {
                return horse != null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int sack = 0; sack < HorseCargoData.MAX_SACKS; sack++) {
            final int sackIndex = sack;
            addSlot(new Slot(sackInventory, sack, 30, 18 + sack * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return isActive() && HorseCargoData.isEmptySack(stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return cargo != null && HorseCargoData.isCargoRowEmpty(cargo, sackIndex);
                }

                @Override
                public boolean isActive() {
                    return sackIndex < currentCapacity();
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < HorseCargoData.MAX_SACKS; row++) {
            final int sackIndex = row;
            for (int column = 0; column < HorseCargoData.SLOTS_PER_SACK; column++) {
                int cargoIndex = row * HorseCargoData.SLOTS_PER_SACK + column;
                addSlot(new Slot(cargoInventory, cargoIndex, 64 + column * 18, 18 + row * 18) {
                    @Override
                    public boolean isActive() {
                        return sacks != null && sackIndex < currentCapacity() && !sacks.getItem(sackIndex).isEmpty();
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return isActive() && !HorseCargoData.isSack(stack);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return isActive();
                    }
                });
            }
        }

        int playerY = 140;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 64 + column * 18, playerY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 64 + column * 18, playerY + 58));
        }
    }

    private static Horse findHorse(Inventory inventory, int entityId) {
        Entity entity = inventory.player.level().getEntity(entityId);
        return entity instanceof Horse horse ? horse : null;
    }

    public Horse horse() {
        return horse;
    }

    private int currentCapacity() {
        if (horse == null) {
            return 0;
        }
        Item armorItem = horse.getArmor().getItem();
        int innateCapacity = HorseCargoData.innateCapacity(horse);
        if (armorItem != cachedArmorItem || innateCapacity != cachedInnateCapacity) {
            cachedArmorItem = armorItem;
            cachedInnateCapacity = innateCapacity;
            cachedTotalCapacity = Math.min(HorseCargoData.MAX_SACKS,
                    innateCapacity + HorseArmorStats.of(armorItem).sackCapacity());
        }
        return cachedTotalCapacity;
    }

    @Override
    public boolean stillValid(Player player) {
        return horse != null && horse.isAlive() && horse.distanceTo(player) < 8.0F;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (!slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (index < HORSE_SLOT_COUNT) {
            if (!moveItemStackTo(source, HORSE_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (getSlot(0).mayPlace(source) && !getSlot(0).hasItem()) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (getSlot(1).mayPlace(source) && !getSlot(1).hasItem()) {
            if (!moveItemStackTo(source, 1, 2, false)) return ItemStack.EMPTY;
        } else if (HorseCargoData.isEmptySack(source)) {
            if (!moveItemStackTo(source, 2, 2 + HorseCargoData.MAX_SACKS, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 2 + HorseCargoData.MAX_SACKS, HORSE_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, source);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        equipment.stopOpen(player);
        if (sacks != null) sacks.setChanged();
        if (cargo != null) cargo.setChanged();
    }
}
