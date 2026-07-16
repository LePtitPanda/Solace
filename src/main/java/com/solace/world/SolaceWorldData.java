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
    private static final String INTRO_VILLAGE_LOCATOR_VERSION_KEY = "BreezyVillageIntroVillageLocatorVersion";
    private static final String INTRO_ROUTE_PRELOADED_KEY = "BreezyVillageIntroRoutePreloaded";
    private static final String INTRO_ROUTE_PRELOAD_VERSION_KEY = "BreezyVillageIntroRoutePreloadVersion";

    private final Set<UUID> breezyVillageIntroCompletedPlayers = new HashSet<>();
    private boolean breezyVillageIntroVillageFound;
    private BlockPos breezyVillageIntroVillagePos;
    private int breezyVillageIntroVillageLocatorVersion;
    private boolean breezyVillageIntroRoutePreloaded;
    private int breezyVillageIntroRoutePreloadVersion;

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
        data.breezyVillageIntroVillageLocatorVersion = tag.getInt(INTRO_VILLAGE_LOCATOR_VERSION_KEY);
        data.breezyVillageIntroRoutePreloaded = tag.getBoolean(INTRO_ROUTE_PRELOADED_KEY);
        data.breezyVillageIntroRoutePreloadVersion = tag.getInt(INTRO_ROUTE_PRELOAD_VERSION_KEY);
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

    public boolean hasBreezyVillageIntroVillagePos(int requiredLocatorVersion) {
        return hasBreezyVillageIntroVillagePos()
                && breezyVillageIntroVillageLocatorVersion >= requiredLocatorVersion;
    }

    public BlockPos getBreezyVillageIntroVillagePos() {
        return breezyVillageIntroVillagePos;
    }

    public void setBreezyVillageIntroVillagePos(BlockPos pos) {
        setBreezyVillageIntroVillagePos(pos, breezyVillageIntroVillageLocatorVersion);
    }

    public void setBreezyVillageIntroVillagePos(BlockPos pos, int locatorVersion) {
        boolean locationChanged = !java.util.Objects.equals(this.breezyVillageIntroVillagePos, pos)
                || this.breezyVillageIntroVillageLocatorVersion != locatorVersion;
        this.breezyVillageIntroVillageFound = pos != null;
        this.breezyVillageIntroVillagePos = pos;
        this.breezyVillageIntroVillageLocatorVersion = pos == null ? 0 : locatorVersion;
        if (locationChanged) {
            // A route marker is only valid for the exact village produced by
            // the corresponding locator implementation.
            this.breezyVillageIntroRoutePreloaded = false;
            this.breezyVillageIntroRoutePreloadVersion = 0;
        }
        setDirty();
    }

    public boolean isBreezyVillageIntroRoutePreloaded(int requiredVersion) {
        return breezyVillageIntroRoutePreloaded && breezyVillageIntroRoutePreloadVersion >= requiredVersion;
    }

    public void setBreezyVillageIntroRoutePreloaded(int version) {
        if (!this.breezyVillageIntroRoutePreloaded || this.breezyVillageIntroRoutePreloadVersion != version) {
            this.breezyVillageIntroRoutePreloaded = true;
            this.breezyVillageIntroRoutePreloadVersion = version;
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
        tag.putInt(INTRO_VILLAGE_LOCATOR_VERSION_KEY, breezyVillageIntroVillageLocatorVersion);
        tag.putBoolean(INTRO_ROUTE_PRELOADED_KEY, breezyVillageIntroRoutePreloaded);
        tag.putInt(INTRO_ROUTE_PRELOAD_VERSION_KEY, breezyVillageIntroRoutePreloadVersion);
        if (breezyVillageIntroVillageFound && breezyVillageIntroVillagePos != null) {
            tag.putInt(INTRO_VILLAGE_X_KEY, breezyVillageIntroVillagePos.getX());
            tag.putInt(INTRO_VILLAGE_Y_KEY, breezyVillageIntroVillagePos.getY());
            tag.putInt(INTRO_VILLAGE_Z_KEY, breezyVillageIntroVillagePos.getZ());
        }
        return tag;
    }
}
