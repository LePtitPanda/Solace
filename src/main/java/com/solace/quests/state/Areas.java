package com.solace.quests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.TreeMap;

/**
 * Named areas, world-wide ("bellhaven" is the same place for every player). An area is a column:
 * a horizontal box in one dimension, all heights. Set by the solace:set_area effect or /sq area.
 */
public final class Areas extends SavedData {
    private static final String NAME = "solace_quest_areas";

    public record Area(String dimension, int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(String dim, double x, double z) {
            return dimension.equals(dim) && x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
        }

        public static Area around(String dimension, int x, int z, int radius) {
            return new Area(dimension, x - radius, z - radius, x + radius, z + radius);
        }

        @Override
        public String toString() {
            return dimension + " [" + minX + ", " + minZ + "] to [" + maxX + ", " + maxZ + "]";
        }
    }

    private final Map<String, Area> areas = new TreeMap<>();

    public static Areas get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Areas::load, Areas::new, NAME);
    }

    public Area area(String name) {
        return areas.get(name);
    }

    public Map<String, Area> all() {
        return java.util.Collections.unmodifiableMap(areas);
    }

    /** Returns false (and changes nothing) when the area exists and {@code replace} is false. */
    public boolean set(String name, Area area, boolean replace) {
        if (!replace && areas.containsKey(name)) {
            return false;
        }
        areas.put(name, area);
        setDirty();
        return true;
    }

    public boolean remove(String name) {
        boolean removed = areas.remove(name) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean contains(String name, String dimension, double x, double z) {
        Area area = areas.get(name);
        return area != null && area.contains(dimension, x, z);
    }

    private static Areas load(CompoundTag tag) {
        Areas data = new Areas();
        for (String key : tag.getAllKeys()) {
            CompoundTag a = tag.getCompound(key);
            data.areas.put(key, new Area(a.getString("dimension"), a.getInt("minX"), a.getInt("minZ"),
                    a.getInt("maxX"), a.getInt("maxZ")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        areas.forEach((name, area) -> {
            CompoundTag a = new CompoundTag();
            a.putString("dimension", area.dimension());
            a.putInt("minX", area.minX());
            a.putInt("minZ", area.minZ());
            a.putInt("maxX", area.maxX());
            a.putInt("maxZ", area.maxZ());
            tag.put(name, a);
        });
        return tag;
    }
}
