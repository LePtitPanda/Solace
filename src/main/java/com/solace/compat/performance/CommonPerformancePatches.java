package com.solace.compat.performance;

import com.google.gson.Gson;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Startup-only common switches, readable by the mixin plugin without loading game classes. */
public final class CommonPerformancePatches {
    private static final Settings SETTINGS = readSettings();

    private CommonPerformancePatches() { }

    public static boolean noiseChunkIdentityWrap() {
        return SETTINGS.enabled && SETTINGS.noiseChunkIdentityWrap
                && Boolean.parseBoolean(System.getProperty("solace.commonPerformance", "true"))
                && Boolean.parseBoolean(System.getProperty("solace.commonPerformance.noiseChunkIdentityWrap", "true"));
    }

    private static Settings readSettings() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("solace-performance.json");
        if (Files.isRegularFile(path)) {
            try (Reader input = Files.newBufferedReader(path)) {
                Settings settings = new Gson().fromJson(input, Settings.class);
                if (settings != null) return settings;
            } catch (Exception exception) {
                LogManager.getLogger("Solace/CommonPerformance").warn("Unreadable common performance switches; retaining original behavior", exception);
                Settings disabled = new Settings();
                disabled.enabled = false;
                return disabled;
            }
        }
        return new Settings();
    }

    private static final class Settings {
        boolean enabled = true;
        boolean noiseChunkIdentityWrap = false;
    }
}
