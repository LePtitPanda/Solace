package com.solace.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolaceConfigPathsTest {
    @TempDir
    static Path game;

    @BeforeAll
    static void paths() throws Exception {
        FMLPaths.loadAbsolutePaths(game);
        Files.createDirectories(FMLPaths.CONFIGDIR.get());
    }

    @Test
    void movesLegacyFileOnceAndNeverOverwrites() throws Exception {
        Path legacy = FMLPaths.CONFIGDIR.get().resolve("solace-sample.json");
        Files.writeString(legacy, "old");

        Path moved = SolaceConfigPaths.resolve("sample.json");
        assertEquals(FMLPaths.CONFIGDIR.get().resolve("solace").resolve("sample.json"), moved);
        assertEquals("old", Files.readString(moved));
        assertFalse(Files.exists(legacy));

        Files.writeString(legacy, "stale copy");
        SolaceConfigPaths.resolve("sample.json");
        assertEquals("old", Files.readString(moved), "an existing new-path file wins");
        assertTrue(Files.exists(legacy));
    }

    @Test
    void onceBuildingsComeBeforeRangeBuildingsInFileOrder() throws Exception {
        Path config = SolaceConfigPaths.dir().resolve("village-buildings.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, """
                {"entries": {
                  "test:range": {"mode": "RANGE", "min": 2, "max": 3},
                  "test:once_b": {"mode": "ONCE", "min": 1, "max": 1},
                  "test:plain": {"mode": "DEFAULT", "min": 1, "max": 1},
                  "test:once_d": {"mode": "ONCE", "min": 1, "max": 1}
                }}""");

        List<ResourceLocation> ids = VillageBuildingSpawnConfig.customBuildingIds();
        assertEquals(new ResourceLocation("test", "once_b"), ids.get(0));
        assertEquals(new ResourceLocation("test", "once_d"), ids.get(1));
        assertEquals(new ResourceLocation("test", "range"), ids.get(ids.size() - 1), "RANGE after every ONCE, defaults included");
        assertFalse(ids.contains(new ResourceLocation("test", "plain")));
    }
}
