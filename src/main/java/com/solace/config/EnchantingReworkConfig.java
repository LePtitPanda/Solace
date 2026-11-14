package com.solace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class EnchantingReworkConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = SolaceConfigPaths.resolve("enchanting-rework.json");

    private static ConfigData data;

    private EnchantingReworkConfig() {
    }

    public static synchronized void init() {
        load();
    }

    public static synchronized void reload() {
        data = null;
        load();
        LOGGER.info("[Solace] Reloaded artifact enchanting config");
    }

    public static List<ResourceLocation> artifactEnchantments() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String key : load().artifactEnchantments) {
            try {
                ids.add(new ResourceLocation(key));
            } catch (RuntimeException e) {
                LOGGER.warn("[Solace] Ignoring invalid identified artifact enchantment id {}", key);
            }
        }
        return Collections.unmodifiableList(ids);
    }

    public static int enchantingLapisCost() {
        return Math.max(0, load().enchantingLapisCost);
    }

    public static int rerollLapisCost() {
        return Math.max(0, load().rerollLapisCost);
    }

    public static int rerollXpCost() {
        return Math.max(0, load().rerollXpCost);
    }

    private static ConfigData load() {
        if (data != null) {
            return data;
        }

        ConfigData loaded = null;
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                loaded = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("[Solace] Could not read {}; using enchanting rework defaults for this launch.", CONFIG_PATH, e);
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
            LOGGER.warn("[Solace] Could not save enchanting rework config {}", CONFIG_PATH, e);
        }
    }

    private static final class ConfigData {
        public List<String> artifactEnchantments = new ArrayList<>(List.of(
                "betterarcheology:penetrating_strike",
                "betterarcheology:soaring_winds",
                "betterarcheology:tunneling"
        ));
        public int enchantingLapisCost = 1;
        public int rerollLapisCost = 1;
        public int rerollXpCost = 1;

        private boolean sanitize() {
            boolean changed = false;
            if (artifactEnchantments == null || artifactEnchantments.isEmpty()) {
                artifactEnchantments = new ArrayList<>(List.of(
                        "betterarcheology:penetrating_strike",
                        "betterarcheology:soaring_winds",
                        "betterarcheology:tunneling"
                ));
                changed = true;
            }
            if (enchantingLapisCost < 0) {
                enchantingLapisCost = 0;
                changed = true;
            }
            if (rerollLapisCost < 0) {
                rerollLapisCost = 0;
                changed = true;
            }
            if (rerollXpCost < 0) {
                rerollXpCost = 0;
                changed = true;
            }
            return changed;
        }
    }
}
