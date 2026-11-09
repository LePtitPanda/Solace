package com.solace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small, deliberately conservative configuration surface for Solace's
 * integrated-server performance experiments.
 */
public final class PerformanceConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("solace-performance.json");

    private static ConfigData data;

    private PerformanceConfig() {
    }

    public static synchronized void init() {
        load();
    }

    public static synchronized void reload() {
        data = null;
        load();
    }

    public static boolean isEnabledFor(MinecraftServer server) {
        ConfigData config = load();
        return config.enabled
                && server != null
                && (!config.singleplayerOnly || !server.isDedicatedServer());
    }

    public static boolean isSingleplayerOnly() {
        return load().singleplayerOnly;
    }

    public static int healthyPoiQueriesPerTick() {
        return load().healthyPoiQueriesPerTick;
    }

    public static int pressuredPoiQueriesPerTick() {
        return load().pressuredPoiQueriesPerTick;
    }

    public static double poiPressureThresholdMillis() {
        return load().poiPressureThresholdMillis;
    }

    public static int maxPoiSearchRadius() {
        return load().maxPoiSearchRadius;
    }

    public static boolean shouldSkipUnloadedPoiCandidates() {
        return load().skipUnloadedPoiCandidates;
    }

    /** Loaded-only lookups for retryable AI, map updates and game-event listeners. */
    public static boolean isChunkLoadGuardsEnabled(MinecraftServer server) {
        return isEnabledFor(server) && load().chunkLoadGuards;
    }

    public static boolean isPauseSaveCoalescingEnabled(MinecraftServer server) {
        return isEnabledFor(server) && load().pauseSaveCoalescing;
    }

    public static long pauseSaveIntervalNanos() {
        return load().pauseSaveIntervalSeconds * 1_000_000_000L;
    }

    public static boolean isVillageDiagnosticsEnabled() {
        ConfigData config = load();
        return config.enabled && config.villageDiagnostics;
    }

    private static synchronized ConfigData load() {
        if (data != null) {
            return data;
        }

        ConfigData loaded = null;
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                loaded = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException | JsonSyntaxException | IllegalStateException e) {
                LOGGER.warn("[Solace] Could not read {}; using performance defaults for this launch.", CONFIG_PATH, e);
            }
        }

        if (loaded == null) {
            loaded = new ConfigData();
        }

        boolean changed = loaded.sanitize();
        data = loaded;
        if (changed || !Files.isRegularFile(CONFIG_PATH)) {
            save(data);
        }
        return data;
    }

    private static void save(ConfigData config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[Solace] Could not save performance config {}.", CONFIG_PATH, e);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class ConfigData {
        public boolean enabled = true;
        public boolean singleplayerOnly = true;

        public int healthyPoiQueriesPerTick = 2;
        public int pressuredPoiQueriesPerTick = 1;
        public double poiPressureThresholdMillis = 35.0D;
        public int maxPoiSearchRadius = 64;
        public boolean skipUnloadedPoiCandidates = true;

        public boolean chunkLoadGuards = true;

        public boolean pauseSaveCoalescing = false;
        public int pauseSaveIntervalSeconds = 90;

        public boolean villageDiagnostics = false;

        private boolean sanitize() {
            boolean changed = false;

            int sanitizedHealthyBudget = clamp(healthyPoiQueriesPerTick, 1, 64);
            changed |= sanitizedHealthyBudget != healthyPoiQueriesPerTick;
            healthyPoiQueriesPerTick = sanitizedHealthyBudget;

            int sanitizedPressuredBudget = clamp(pressuredPoiQueriesPerTick, 1, healthyPoiQueriesPerTick);
            changed |= sanitizedPressuredBudget != pressuredPoiQueriesPerTick;
            pressuredPoiQueriesPerTick = sanitizedPressuredBudget;

            double sanitizedThreshold = clamp(poiPressureThresholdMillis, 5.0D, 1_000.0D);
            changed |= Double.compare(sanitizedThreshold, poiPressureThresholdMillis) != 0;
            poiPressureThresholdMillis = sanitizedThreshold;

            int sanitizedRadius = clamp(maxPoiSearchRadius, 1, 512);
            changed |= sanitizedRadius != maxPoiSearchRadius;
            maxPoiSearchRadius = sanitizedRadius;

            int sanitizedPauseInterval = clamp(pauseSaveIntervalSeconds, 5, 3_600);
            changed |= sanitizedPauseInterval != pauseSaveIntervalSeconds;
            pauseSaveIntervalSeconds = sanitizedPauseInterval;

            return changed;
        }
    }
}
