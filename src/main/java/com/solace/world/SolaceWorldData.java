package com.solace.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class SolaceWorldData extends SavedData {
    private static final String INTRO_COMPLETED_KEY = "BreezyVillageIntroCompleted";

    private boolean breezyVillageIntroCompleted;

    public static SolaceWorldData load(CompoundTag tag) {
        SolaceWorldData data = new SolaceWorldData();
        data.breezyVillageIntroCompleted = tag.getBoolean(INTRO_COMPLETED_KEY);
        return data;
    }

    public boolean isBreezyVillageIntroCompleted() {
        return breezyVillageIntroCompleted;
    }

    public void setBreezyVillageIntroCompleted(boolean completed) {
        this.breezyVillageIntroCompleted = completed;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(INTRO_COMPLETED_KEY, breezyVillageIntroCompleted);
        return tag;
    }
}
