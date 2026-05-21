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

public class SolaceWorldData extends SavedData {
    private static final String INTRO_COMPLETED_KEY = "BreezyVillageIntroCompleted";
    private static final String INTRO_COMPLETED_PLAYERS_KEY = "BreezyVillageIntroCompletedPlayers";
    private static final String INTRO_VILLAGE_FOUND_KEY = "BreezyVillageIntroVillageFound";
    private static final String INTRO_VILLAGE_X_KEY = "BreezyVillageIntroVillageX";
    private static final String INTRO_VILLAGE_Y_KEY = "BreezyVillageIntroVillageY";
    private static final String INTRO_VILLAGE_Z_KEY = "BreezyVillageIntroVillageZ";
    private static final String INTRO_ROUTE_PRELOADED_KEY = "BreezyVillageIntroRoutePreloaded";

    private final Set<UUID> breezyVillageIntroCompletedPlayers = new HashSet<>();
    private boolean breezyVillageIntroVillageFound;
    private BlockPos breezyVillageIntroVillagePos;
    private boolean breezyVillageIntroRoutePreloaded;

    public static SolaceWorldData load(CompoundTag tag) {
        SolaceWorldData data = new SolaceWorldData();
        ListTag completedPlayers = tag.getList(INTRO_COMPLETED_PLAYERS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < completedPlayers.size(); i++) {
            try {
                data.breezyVillageIntroCompletedPlayers.add(UUID.fromString(completedPlayers.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.breezyVillageIntroVillageFound = tag.getBoolean(INTRO_VILLAGE_FOUND_KEY);
        data.breezyVillageIntroRoutePreloaded = tag.getBoolean(INTRO_ROUTE_PRELOADED_KEY);
        if (data.breezyVillageIntroVillageFound) {
            data.breezyVillageIntroVillagePos = new BlockPos(
                    tag.getInt(INTRO_VILLAGE_X_KEY),
                    tag.getInt(INTRO_VILLAGE_Y_KEY),
                    tag.getInt(INTRO_VILLAGE_Z_KEY)
            );
        }
        return data;
    }

    public boolean hasPlayerCompletedBreezyVillageIntro(UUID playerId) {
        return breezyVillageIntroCompletedPlayers.contains(playerId);
    }

    public void setPlayerCompletedBreezyVillageIntro(UUID playerId) {
        if (breezyVillageIntroCompletedPlayers.add(playerId)) {
            setDirty();
        }
    }

    public boolean hasBreezyVillageIntroVillagePos() {
        return breezyVillageIntroVillageFound && breezyVillageIntroVillagePos != null;
    }

    public BlockPos getBreezyVillageIntroVillagePos() {
        return breezyVillageIntroVillagePos;
    }

    public void setBreezyVillageIntroVillagePos(BlockPos pos) {
        this.breezyVillageIntroVillageFound = pos != null;
        this.breezyVillageIntroVillagePos = pos;
        setDirty();
    }

    public boolean isBreezyVillageIntroRoutePreloaded() {
        return breezyVillageIntroRoutePreloaded;
    }

    public void setBreezyVillageIntroRoutePreloaded(boolean preloaded) {
        if (this.breezyVillageIntroRoutePreloaded != preloaded) {
            this.breezyVillageIntroRoutePreloaded = preloaded;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.remove(INTRO_COMPLETED_KEY);
        ListTag completedPlayers = new ListTag();
        for (UUID playerId : breezyVillageIntroCompletedPlayers) {
            completedPlayers.add(StringTag.valueOf(playerId.toString()));
        }
        tag.put(INTRO_COMPLETED_PLAYERS_KEY, completedPlayers);
        tag.putBoolean(INTRO_VILLAGE_FOUND_KEY, breezyVillageIntroVillageFound);
        tag.putBoolean(INTRO_ROUTE_PRELOADED_KEY, breezyVillageIntroRoutePreloaded);
        if (breezyVillageIntroVillageFound && breezyVillageIntroVillagePos != null) {
            tag.putInt(INTRO_VILLAGE_X_KEY, breezyVillageIntroVillagePos.getX());
            tag.putInt(INTRO_VILLAGE_Y_KEY, breezyVillageIntroVillagePos.getY());
            tag.putInt(INTRO_VILLAGE_Z_KEY, breezyVillageIntroVillagePos.getZ());
        }
        return tag;
    }
}
