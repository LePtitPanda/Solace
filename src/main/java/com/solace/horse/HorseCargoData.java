package com.solace.horse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class HorseCargoData {
    public static final int MAX_SACKS = 6;
    public static final int SLOTS_PER_SACK = 9;
    public static final int MAX_CARGO_SLOTS = MAX_SACKS * SLOTS_PER_SACK;
    private static final String ROOT = "SolaceHorseCargo";
    private static final String SACKS = "Sacks";
    private static final String CARGO = "Cargo";
    private static final String INITIALIZED = "CapacityInitialized";

    private HorseCargoData() {
    }

    public static PersistentContainer sacks(Horse horse) {
        return new PersistentContainer(horse, SACKS, MAX_SACKS);
    }

    public static PersistentContainer cargo(Horse horse) {
        return new PersistentContainer(horse, CARGO, MAX_CARGO_SLOTS);
    }

    public static int innateCapacity(Horse horse) {
        if (HorseCargoRegistry.SACK_CAPACITY == null || horse.getAttribute(HorseCargoRegistry.SACK_CAPACITY) == null) {
            return 0;
        }
        return (int) Math.round(horse.getAttribute(HorseCargoRegistry.SACK_CAPACITY).getBaseValue());
    }

    public static int totalCapacity(Horse horse) {
        return Math.min(MAX_SACKS, innateCapacity(horse) + HorseArmorStats.of(horse.getArmor()).sackCapacity());
    }

    public static boolean initializeCapacity(Horse horse) {
        CompoundTag root = root(horse);
        if (root.getBoolean(INITIALIZED)) {
            return false;
        }
        int capacity = horse.getRandom().nextInt(3);
        setInnateCapacity(horse, capacity);
        root.putBoolean(INITIALIZED, true);
        horse.getPersistentData().put(ROOT, root);
        return true;
    }

    public static void setInheritedCapacity(Horse horse, int value) {
        setInnateCapacity(horse, Math.max(0, Math.min(2, value)));
        CompoundTag root = root(horse);
        root.putBoolean(INITIALIZED, true);
        horse.getPersistentData().put(ROOT, root);
    }

    private static void setInnateCapacity(Horse horse, int value) {
        if (HorseCargoRegistry.SACK_CAPACITY != null && horse.getAttribute(HorseCargoRegistry.SACK_CAPACITY) != null) {
            horse.getAttribute(HorseCargoRegistry.SACK_CAPACITY).setBaseValue(value);
        }
    }

    public static boolean canUseCapacity(SimpleContainer sacks, SimpleContainer cargo, int newCapacity) {
        for (int sack = Math.max(0, newCapacity); sack < MAX_SACKS; sack++) {
            if (!sacks.getItem(sack).isEmpty() || !isCargoRowEmpty(cargo, sack)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCargoRowEmpty(SimpleContainer cargo, int sackIndex) {
        int start = sackIndex * SLOTS_PER_SACK;
        for (int slot = start; slot < start + SLOTS_PER_SACK; slot++) {
            if (!cargo.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isEmptySack(ItemStack stack) {
        if (!isSack(stack)) {
            return false;
        }
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        return blockEntityTag == null || !blockEntityTag.contains("Items", 9) || blockEntityTag.getList("Items", 10).isEmpty();
    }

    public static boolean isSack(ItemStack stack) {
        return ResourceLocationHolder.of(stack).matches("supplementaries", "sack");
    }

    public static boolean hasAnyCargo(Horse horse) {
        PersistentContainer sacks = sacks(horse);
        PersistentContainer cargo = cargo(horse);
        for (int i = 0; i < sacks.getContainerSize(); i++) {
            if (!sacks.getItem(i).isEmpty()) {
                return true;
            }
        }
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            if (!cargo.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static void clear(Horse horse) {
        CompoundTag root = root(horse);
        root.remove(SACKS);
        root.remove(CARGO);
        horse.getPersistentData().put(ROOT, root);
    }

    private static CompoundTag root(Horse horse) {
        return horse.getPersistentData().contains(ROOT, 10)
                ? horse.getPersistentData().getCompound(ROOT)
                : new CompoundTag();
    }

    public static final class PersistentContainer extends SimpleContainer {
        private final Horse horse;
        private final String key;
        private boolean loading;

        private PersistentContainer(Horse horse, String key, int size) {
            super(size);
            this.horse = horse;
            this.key = key;
            loading = true;
            ListTag items = root(horse).getCompound(key).getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot < getContainerSize()) {
                    super.setItem(slot, ItemStack.of(itemTag));
                }
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) {
                return;
            }
            CompoundTag root = root(horse);
            CompoundTag data = new CompoundTag();
            ListTag items = new ListTag();
            for (int slot = 0; slot < getContainerSize(); slot++) {
                ItemStack stack = getItem(slot);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = stack.save(new CompoundTag());
                    itemTag.putByte("Slot", (byte) slot);
                    items.add(itemTag);
                }
            }
            data.put("Items", items);
            root.put(key, data);
            horse.getPersistentData().put(ROOT, root);
        }
    }

    private record ResourceLocationHolder(String namespace, String path) {
        static ResourceLocationHolder of(ItemStack stack) {
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id == null ? new ResourceLocationHolder("", "") : new ResourceLocationHolder(id.getNamespace(), id.getPath());
        }

        boolean matches(String namespace, String path) {
            return this.namespace.equals(namespace) && this.path.equals(path);
        }
    }
}
