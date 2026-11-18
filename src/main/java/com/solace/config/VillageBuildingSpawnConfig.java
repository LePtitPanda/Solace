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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class VillageBuildingSpawnConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = SolaceConfigPaths.resolve("village-buildings.json");
    private static final List<ResourceLocation> DEFAULT_CUSTOM_BUILDINGS = List.of(
            id("villagersplus", "village/plains/plains_alchemist"),
            id("villagersplus", "village/plains/plains_horticulturist"),
            id("villagersplus", "village/plains/plains_occultist"),
            id("villagersplus", "village/plains/plains_oceanographer"),
            id("chefsdelight", "plains_chef_house"),
            id("chefsdelight", "plains_cook_house")
    );

    private static ConfigData data;

    private VillageBuildingSpawnConfig() {
    }

    public static synchronized void init() {
        load();
    }

    public static synchronized Entry discover(ResourceLocation buildingId) {
        ConfigData config = load();
        String key = buildingId.toString();
        Entry entry = config.entries.get(key);
        if (entry == null) {
            entry = defaultEntry(buildingId);
            config.entries.put(key, entry);
            save(config);
            LOGGER.info("[Solace] Added discovered plains village building to config: {} -> {}", key, entry.mode);
            return entry.copy();
        }

        Entry sanitized = sanitize(entry, buildingId);
        if (!sanitized.equals(entry)) {
            config.entries.put(key, sanitized);
            save(config);
            entry = sanitized;
        }
        return entry.copy();
    }

    public static synchronized List<String> describeEntries() {
        ConfigData config = load();
        List<String> entries = new ArrayList<>(config.entries.size());
        for (Map.Entry<String, Entry> entry : config.entries.entrySet()) {
            Entry rule = sanitize(entry.getValue(), parseId(entry.getKey()));
            entries.add(entry.getKey() + "=" + rule.mode + "[" + rule.min + "," + rule.max + "]");
        }
        return Collections.unmodifiableList(entries);
    }

    /** Controlled buildings in placement priority: every ONCE entry, then every RANGE entry, each in file order. */
    public static synchronized List<ResourceLocation> customBuildingIds() {
        ConfigData config = load();
        List<ResourceLocation> onceIds = new ArrayList<>();
        List<ResourceLocation> rangeIds = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : config.entries.entrySet()) {
            ResourceLocation id = parseId(entry.getKey());
            Entry rule = sanitize(entry.getValue(), id);
            if (rule.mode == Mode.ONCE) {
                onceIds.add(id);
            } else if (rule.mode == Mode.RANGE) {
                rangeIds.add(id);
            }
        }
        onceIds.addAll(rangeIds);
        return Collections.unmodifiableList(onceIds);
    }

    public static List<ResourceLocation> defaultCustomBuildings() {
        return DEFAULT_CUSTOM_BUILDINGS;
    }

    public static int minimumCount(ResourceLocation buildingId) {
        Entry entry = discover(buildingId);
        if (entry.mode == Mode.DEFAULT) {
            return -1;
        }
        if (entry.mode == Mode.ONCE) {
            return 1;
        }
        return entry.min;
    }

    public static int maximumCount(ResourceLocation buildingId) {
        Entry entry = discover(buildingId);
        if (entry.mode == Mode.DEFAULT) {
            return -1;
        }
        if (entry.mode == Mode.ONCE) {
            return 1;
        }
        return entry.max;
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
                LOGGER.warn("[Solace] Could not read {}; using defaults for this launch.", CONFIG_PATH, e);
            }
        }

        if (loaded == null) {
            loaded = new ConfigData();
        }
        if (loaded.entries == null) {
            loaded.entries = new LinkedHashMap<>();
        }

        boolean changed = false;
        if (Files.isRegularFile(CONFIG_PATH)) {
            try {
                String content = Files.readString(CONFIG_PATH);
                if (content.contains("\"RANDOM\"") || content.contains("\"CUSTOM\"")) {
                    changed = true;
                }
            } catch (IOException ignored) {
            }
        }

        for (ResourceLocation buildingId : DEFAULT_CUSTOM_BUILDINGS) {
            String key = buildingId.toString();
            if (!loaded.entries.containsKey(key)) {
                loaded.entries.put(key, Entry.once());
                changed = true;
            }
        }

        Map<String, Entry> sanitizedEntries = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> entry : loaded.entries.entrySet()) {
            ResourceLocation id = parseId(entry.getKey());
            Entry sanitized = sanitize(entry.getValue(), id);
            sanitizedEntries.put(entry.getKey(), sanitized);
            changed |= !sanitized.equals(entry.getValue());
        }
        loaded.entries = sanitizedEntries;
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
            LOGGER.warn("[Solace] Could not save village building config {}", CONFIG_PATH, e);
        }
    }

    private static Entry defaultEntry(ResourceLocation buildingId) {
        if (DEFAULT_CUSTOM_BUILDINGS.contains(buildingId)) {
            return Entry.once();
        }
        return Entry.range(0, 1); // a newly found building may appear at most once
    }

    private static Entry sanitize(Entry entry, ResourceLocation buildingId) {
        if (entry == null) {
            return defaultEntry(buildingId);
        }

        Mode mode = entry.mode == null ? Mode.DEFAULT : entry.mode;
        int min = Math.max(0, entry.min);
        int max = Math.max(min, entry.max);
        return new Entry(mode, min, max);
    }

    private static ResourceLocation parseId(String key) {
        try {
            return new ResourceLocation(key);
        } catch (RuntimeException ignored) {
            return id("solace", "invalid_config_entry");
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public enum Mode {
        @SerializedName(value = "DEFAULT", alternate = {"RANDOM"})
        DEFAULT,
        @SerializedName(value = "ONCE")
        ONCE,
        @SerializedName(value = "RANGE", alternate = {"CUSTOM"})
        RANGE
    }

    public static final class Entry {
        public Mode mode = Mode.DEFAULT;
        public int min = 1;
        public int max = 1;

        public Entry() {
        }

        private Entry(Mode mode, int min, int max) {
            this.mode = mode;
            this.min = min;
            this.max = max;
        }

        private static Entry defaultMode() {
            return new Entry(Mode.DEFAULT, 1, 1);
        }

        private static Entry once() {
            return new Entry(Mode.ONCE, 1, 1);
        }

        private static Entry range(int min, int max) {
            return new Entry(Mode.RANGE, min, max);
        }

        private Entry copy() {
            return new Entry(mode, min, max);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Entry entry)) {
                return false;
            }
            return mode == entry.mode && min == entry.min && max == entry.max;
        }

        @Override
        public int hashCode() {
            int result = mode.hashCode();
            result = 31 * result + min;
            result = 31 * result + max;
            return result;
        }
    }

    private static final class ConfigData {
        public Map<String, Entry> entries = new LinkedHashMap<>();
    }
}
