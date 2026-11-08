package com.solace.compat.terrainslabs;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Extends Countered's Terrain Slabs to modded biome ground blocks that the mod does not cover.
 * Everything that touches Terrain Slabs classes lives in {@link TerrainSlabsContent}, which is only
 * loaded when the mod is present.
 */
public final class TerrainSlabsCompat {
    public static final String MOD_ID = "terrain_slabs";

    private TerrainSlabsCompat() {
    }

    public static void register(IEventBus modBus) {
        if (ModList.get().isLoaded(MOD_ID)) {
            TerrainSlabsContent.register(modBus);
        }
    }
}
