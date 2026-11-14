package com.solace.compat.performance;

import com.solace.config.SolaceConfigPaths;
import com.google.gson.Gson;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Startup-only switches: unknown mod versions and the Radiance profile retain original behavior. */
public final class ClientPerformancePatches {
    private static final Settings SETTINGS = readSettings();

    private ClientPerformancePatches() { }

    public static boolean bannerIndex() {
        return enabled("bannerIndex", SETTINGS.bannerIndex) && version("block_factorys_bosses", "1.0.9.1");
    }

    public static boolean gliderBlockGate() {
        return enabled("gliderBlockGate", SETTINGS.gliderBlockGate) && version("vc_gliders", "1.1.6");
    }

    public static boolean sheepVariantRead() {
        return enabled("sheepVariantRead", SETTINGS.sheepVariantRead) && version("moremobvariants", "1.3.0.1");
    }

    public static boolean lightCacheMissPositions() {
        return enabled("lightCacheMissPositions", SETTINGS.lightCacheMissPositions)
                && version("create", "6.0.8")
                && (version("ponder", "1.0.91") || version("ponder", "1.0.92"));
    }

    public static boolean fzzyConditionCache() {
        return enabled("fzzyConditionCache", SETTINGS.fzzyConditionCache) && version("fzzy_config", "0.7.7+1.20.1+forge");
    }

    public static boolean dynamicFpsLoading() {
        return enabled("dynamicFpsLoading", SETTINGS.dynamicFpsLoading) && version("dynamic_fps", "3.11.4");
    }

    public static boolean bannerPatternLod() {
        return enabled("bannerPatternLod", SETTINGS.bannerPatternLod) && SETTINGS.bannerLodDistance > 0;
    }

    public static double bannerLodDistance() {
        return SETTINGS.bannerLodDistance;
    }

    public static boolean musicIdleWait() {
        return enabled("musicIdleWait", SETTINGS.musicIdleWait) && version("reactivemusic", "1.3.5");
    }

    public static boolean visibleSpriteCache() {
        return enabled("visibleSpriteCache", SETTINGS.visibleSpriteCache) && version("embeddium", "0.3.32-beta.9999+mc1.20.1");
    }

    public static boolean mergedDrawCommands() {
        return enabled("mergedDrawCommands", SETTINGS.mergedDrawCommands) && version("embeddium", "0.3.32-beta.9999+mc1.20.1");
    }

    public static boolean trialSpawnerModelWrap() {
        return enabled("trialSpawnerModelWrap", SETTINGS.trialSpawnerModelWrap);
    }

    public static boolean ambientBiomeCells() {
        return enabled("ambientBiomeCells", SETTINGS.ambientBiomeCells);
    }

    public static boolean xaeroMapWriteBudget() {
        return enabled("xaeroMapWriteBudget", SETTINGS.xaeroMapWriteBudget);
    }

    /** Render-thread time Xaero's World Map may spend writing tiles, in ns per elapsed ms. */
    public static long xaeroMapWriteNanosPerMilli() {
        double percent = Math.max(0.5D, Math.min(20.0D, SETTINGS.xaeroMapWritePercent));
        return Math.round(percent * 10_000.0D);
    }

    /** Chunks Xaero's World Map may re-write per second while updating mapped terrain. */
    public static int xaeroMapChunksPerSecond() {
        return Math.max(100, Math.min(3000, SETTINGS.xaeroMapChunksPerSecond));
    }

    public static boolean immediatelyFastHudBatching() {
        return enabled("immediatelyFastHudBatching", SETTINGS.immediatelyFastHudBatching);
    }

    private static boolean enabled(String name, boolean setting) {
        return SETTINGS.enabled && setting
                && !hasMod("radiance")
                && Boolean.parseBoolean(System.getProperty("solace.clientPerformance", "true"))
                && Boolean.parseBoolean(System.getProperty("solace.clientPerformance." + name, "true"));
    }

    private static boolean hasMod(String id) {
        return FMLLoader.getLoadingModList().getMods().stream().anyMatch(mod -> mod.getModId().equals(id));
    }

    private static boolean version(String id, String version) {
        return FMLLoader.getLoadingModList().getMods().stream()
                .anyMatch(mod -> mod.getModId().equals(id) && mod.getVersion().toString().equals(version));
    }

    private static Settings readSettings() {
        Path path = SolaceConfigPaths.resolve("client-performance.json");
        if (Files.isRegularFile(path)) {
            try (Reader input = Files.newBufferedReader(path)) {
                Settings settings = new Gson().fromJson(input, Settings.class);
                if (settings != null) return settings;
            } catch (Exception exception) {
                LogManager.getLogger("Solace/ClientPerformance").warn("Unreadable client performance switches; retaining original mod behavior", exception);
                Settings disabled = new Settings();
                disabled.enabled = false;
                return disabled;
            }
        }
        return new Settings();
    }

    private static final class Settings {
        boolean enabled = true;
        boolean bannerIndex = true;
        boolean gliderBlockGate = true;
        boolean sheepVariantRead = true;
        boolean lightCacheMissPositions = true;
        boolean musicIdleWait = true;
        boolean fzzyConditionCache = true;
        boolean dynamicFpsLoading = true;
        boolean bannerPatternLod = true;
        boolean visibleSpriteCache = true;
        boolean mergedDrawCommands = true;
        boolean trialSpawnerModelWrap = true;
        boolean ambientBiomeCells = true;
        boolean xaeroMapWriteBudget = true;
        double xaeroMapWritePercent = 3.0D;
        int xaeroMapChunksPerSecond = 500;
        boolean immediatelyFastHudBatching = true;
        double bannerLodDistance = 32.0D;
    }
}
