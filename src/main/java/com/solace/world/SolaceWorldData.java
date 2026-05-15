package com.solace.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class SolaceWorldData extends SavedData {
    private static final String INTRO_COMPLETED_KEY = "BreezyVillageIntroCompleted";
    private static final String INTRO_VILLAGE_FOUND_KEY = "BreezyVillageIntroVillageFound";
    private static final String INTRO_VILLAGE_X_KEY = "BreezyVillageIntroVillageX";
    private static final String INTRO_VILLAGE_Y_KEY = "BreezyVillageIntroVillageY";
    private static final String INTRO_VILLAGE_Z_KEY = "BreezyVillageIntroVillageZ";

    private boolean breezyVillageIntroCompleted;
    private boolean breezyVillageIntroVillageFound;
    private BlockPos breezyVillageIntroVillagePos;

    public static SolaceWorldData load(CompoundTag tag) {
        SolaceWorldData data = new SolaceWorldData();
        data.breezyVillageIntroCompleted = tag.getBoolean(INTRO_COMPLETED_KEY);
        data.breezyVillageIntroVillageFound = tag.getBoolean(INTRO_VILLAGE_FOUND_KEY);
        if (data.breezyVillageIntroVillageFound) {
            data.breezyVillageIntroVillagePos = new BlockPos(
                    tag.getInt(INTRO_VILLAGE_X_KEY),
                    tag.getInt(INTRO_VILLAGE_Y_KEY),
                    tag.getInt(INTRO_VILLAGE_Z_KEY)
            );
        }
        return data;
    }

    public boolean isBreezyVillageIntroCompleted() {
        return breezyVillageIntroCompleted;
    }

    public void setBreezyVillageIntroCompleted(boolean completed) {
        this.breezyVillageIntroCompleted = completed;
        setDirty();
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

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(INTRO_COMPLETED_KEY, breezyVillageIntroCompleted);
        tag.putBoolean(INTRO_VILLAGE_FOUND_KEY, breezyVillageIntroVillageFound);
        if (breezyVillageIntroVillageFound && breezyVillageIntroVillagePos != null) {
            tag.putInt(INTRO_VILLAGE_X_KEY, breezyVillageIntroVillagePos.getX());
            tag.putInt(INTRO_VILLAGE_Y_KEY, breezyVillageIntroVillagePos.getY());
            tag.putInt(INTRO_VILLAGE_Z_KEY, breezyVillageIntroVillagePos.getZ());
        }
        return tag;
    }
}
