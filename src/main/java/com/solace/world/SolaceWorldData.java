package com.solace.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Solace's per-world data: the home village and the players already placed in it. */
public class SolaceWorldData extends SavedData {
    // Keys keep their old (balloon intro) names so existing worlds keep their home village and placed players.
    private static final String PLACED_PLAYERS_KEY = "BreezyVillageIntroCompletedPlayers";
    private static final String VILLAGE_FOUND_KEY = "BreezyVillageIntroVillageFound";
    private static final String VILLAGE_X_KEY = "BreezyVillageIntroVillageX";
    private static final String VILLAGE_Y_KEY = "BreezyVillageIntroVillageY";
    private static final String VILLAGE_Z_KEY = "BreezyVillageIntroVillageZ";
    private static final String LOCATOR_VERSION_KEY = "BreezyVillageIntroVillageLocatorVersion";

    private final Set<UUID> placedPlayers = new HashSet<>();
    private BlockPos homeVillage;
    private int locatorVersion;

    public static SolaceWorldData load(CompoundTag tag) {
        SolaceWorldData data = new SolaceWorldData();
        ListTag players = tag.getList(PLACED_PLAYERS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < players.size(); i++) {
            try {
                data.placedPlayers.add(UUID.fromString(players.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (tag.getBoolean(VILLAGE_FOUND_KEY)) {
            data.homeVillage = new BlockPos(tag.getInt(VILLAGE_X_KEY), tag.getInt(VILLAGE_Y_KEY), tag.getInt(VILLAGE_Z_KEY));
            data.locatorVersion = tag.getInt(LOCATOR_VERSION_KEY);
        }
        return data;
    }

    public boolean isPlaced(UUID player) {
        return placedPlayers.contains(player);
    }

    public void setPlaced(UUID player) {
        if (placedPlayers.add(player)) setDirty();
    }

    /** The home village, if it was found by a locator at least this version; else null. */
    public BlockPos homeVillage(int minLocatorVersion) {
        return homeVillage != null && locatorVersion >= minLocatorVersion ? homeVillage : null;
    }

    public void setHomeVillage(BlockPos pos, int locatorVersion) {
        this.homeVillage = pos;
        this.locatorVersion = pos == null ? 0 : locatorVersion;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (UUID player : placedPlayers) players.add(StringTag.valueOf(player.toString()));
        tag.put(PLACED_PLAYERS_KEY, players);
        tag.putBoolean(VILLAGE_FOUND_KEY, homeVillage != null);
        tag.putInt(LOCATOR_VERSION_KEY, locatorVersion);
        if (homeVillage != null) {
            tag.putInt(VILLAGE_X_KEY, homeVillage.getX());
            tag.putInt(VILLAGE_Y_KEY, homeVillage.getY());
            tag.putInt(VILLAGE_Z_KEY, homeVillage.getZ());
        }
        return tag;
    }
}
