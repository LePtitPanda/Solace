package com.solace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Dimension-keyed client loading screen configuration. A dimension is enabled
 * simply by having an object at its resource location in the root JSON object.
 */
public final class DimensionLoadingScreenConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_PATH = SolaceConfigPaths.resolve("loading-screens.json");
    private static final String LEGACY_ICON_FOLDER = "config/solace-loading-screens/";

    static {
        SolaceConfigPaths.resolve("loading-screens"); // the icon folder moves with the config
    }

    private static Map<ResourceLocation, Entry> entries = Map.of();
    private static FileTime loadedTimestamp;
    private static boolean loaded;

    private DimensionLoadingScreenConfig() {
    }

    public static synchronized void init() {
        loadIfChanged();
    }

    @Nullable
    public static synchronized Entry get(ResourceLocation dimension) {
        loadIfChanged();
        return entries.get(dimension);
    }

    private static void loadIfChanged() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            writeDefaults();
        }

        FileTime timestamp = timestamp();
        if (loaded && timestamp != null && timestamp.equals(loadedTimestamp)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<ResourceLocation, Entry> parsed = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> property : root.entrySet()) {
                if (property.getKey().startsWith("_") || !property.getValue().isJsonObject()) {
                    continue;
                }

                ResourceLocation dimension = ResourceLocation.tryParse(property.getKey());
                if (dimension == null) {
                    LOGGER.warn("[Solace] Ignoring invalid loading-screen dimension id {}", property.getKey());
                    continue;
                }

                Entry entry = GSON.fromJson(property.getValue(), Entry.class);
                if (entry != null) {
                    entry.sanitize(dimension);
                    parsed.put(dimension, entry);
                }
            }

            entries = Collections.unmodifiableMap(parsed);
            loadedTimestamp = timestamp;
            loaded = true;
            LOGGER.info("[Solace] Loaded {} configured dimension loading screens", entries.size());
        } catch (Exception e) {
            if (!loaded) {
                entries = defaultEntries();
                loaded = true;
            }
            loadedTimestamp = timestamp;
            LOGGER.warn("[Solace] Could not read {}; keeping the last valid loading-screen configuration", CONFIG_PATH, e);
        }
    }

    private static FileTime timestamp() {
        try {
            return Files.getLastModifiedTime(CONFIG_PATH);
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeDefaults() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_format", 2);
            root.add("_documentation", GSON.toJsonTree(List.of(
                    "A loading screen is enabled when this file contains an object named for that dimension.",
                    "background, blockTexture, and cornerAnimation accept file paths relative to the Minecraft instance or resource locations.",
                    "blockTexture can be any PNG or JPG asset; it does not have to be a Minecraft block.",
                    "Colors use #RRGGBB or #AARRGGBB. Remove a dimension object to use the normal Minecraft screen."
            )));
            defaultEntries().forEach((dimension, entry) -> root.add(dimension.toString(), GSON.toJsonTree(entry)));
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[Solace] Could not create default loading-screen config {}", CONFIG_PATH, e);
        }
    }

    private static Map<ResourceLocation, Entry> defaultEntries() {
        Map<ResourceLocation, Entry> defaults = new LinkedHashMap<>();
        defaults.put(new ResourceLocation("minecraft", "overworld"), Entry.overworld());
        defaults.put(new ResourceLocation("minecraft", "the_nether"), Entry.nether());
        defaults.put(new ResourceLocation("minecraft", "the_end"), Entry.end());
        return Collections.unmodifiableMap(defaults);
    }

    public static final class Entry {
        public String background = "config/fancymenu/assets/firstframe.jpg";
        public String backgroundTint = "#9E061014";
        public String cornerAnimation = "config/fancymenu/assets/loader2.gif";
        public String blockTexture = "config/solace/loading-screens/overworld-icon.png";
        public int cornerAnimationSize = 72;
        public String dimensionLabel = "DIMENSION";
        public String dimensionTitle = "OVERWORLD";
        public String description = "A boundless realm of ancient forests, starlit plains, and hidden wonders. Your adventure begins here.";
        public String tipLabel = "TIP";
        public List<String> tips = new ArrayList<>(List.of(
                "First night? Craft a shelter and light it up. Monsters love the dark.",
                "Explore off the beaten path. Astralis rewards curious travelers.",
                "Waystones and marked landmarks make long expeditions much safer."
        ));
        public String accentColor = "#59D6A3";
        public String dimensionLabelColor = "#65DDAA";
        public String tipLabelColor = "#65DDAA";
        public String primaryTextColor = "#EFF5F3";
        public String secondaryTextColor = "#BBC6C3";
        public String panelColor = "#E60C0C1C";
        public String lineColor = "#59D6A3";

        private static Entry overworld() {
            return new Entry();
        }

        private static Entry nether() {
            Entry entry = new Entry();
            entry.blockTexture = "config/solace/loading-screens/nether-icon.png";
            entry.dimensionTitle = "THE NETHER";
            entry.description = "A burning underworld of ancient fortresses, molten seas, and hostile ground. Prepare before crossing the threshold.";
            entry.tips = new ArrayList<>(List.of(
                    "Carry a piece of gold armor. Piglins respect a traveler who knows their customs.",
                    "Mark your portal coordinates before exploring the Nether.",
                    "Fire resistance can turn a disastrous fall into a narrow escape."
            ));
            entry.accentColor = "#E87258";
            entry.dimensionLabelColor = "#F08A68";
            entry.tipLabelColor = "#F08A68";
            entry.lineColor = entry.accentColor;
            entry.backgroundTint = "#A62A0706";
            return entry;
        }

        private static Entry end() {
            Entry entry = new Entry();
            entry.blockTexture = "config/solace/loading-screens/end-icon.png";
            entry.dimensionTitle = "THE END";
            entry.description = "An endless void scattered with pale islands, lost cities, and the echo of a dragon. Watch every step.";
            entry.tips = new ArrayList<>(List.of(
                    "Bring slow falling potions when exploring high End cities.",
                    "Carved pumpkins keep Endermen calm, but they also narrow your vision.",
                    "Keep spare blocks within reach; the void does not forgive hesitation."
            ));
            entry.accentColor = "#B999E8";
            entry.dimensionLabelColor = "#CBB1F2";
            entry.tipLabelColor = "#CBB1F2";
            entry.lineColor = entry.accentColor;
            entry.backgroundTint = "#A6170D28";
            return entry;
        }

        private void sanitize(ResourceLocation dimension) {
            background = valueOr(background, "");
            backgroundTint = colorOr(backgroundTint, "#A6000000");
            cornerAnimation = valueOr(cornerAnimation, "");
            blockTexture = valueOr(blockTexture, "").replace(LEGACY_ICON_FOLDER, "config/solace/loading-screens/");
            cornerAnimationSize = Math.max(32, Math.min(160, cornerAnimationSize));
            dimensionLabel = valueOr(dimensionLabel, "DIMENSION");
            dimensionTitle = valueOr(dimensionTitle, dimension.getPath().replace('_', ' ').toUpperCase());
            description = valueOr(description, "");
            tipLabel = valueOr(tipLabel, "TIP");
            if (tips == null || tips.isEmpty()) {
                tips = new ArrayList<>(List.of("Adventure awaits."));
            } else {
                tips.removeIf(tip -> tip == null || tip.isBlank());
                if (tips.isEmpty()) {
                    tips.add("Adventure awaits.");
                }
            }
            accentColor = colorOr(accentColor, "#59D6A3");
            dimensionLabelColor = colorOr(dimensionLabelColor, accentColor);
            tipLabelColor = colorOr(tipLabelColor, accentColor);
            primaryTextColor = colorOr(primaryTextColor, "#FFFFFF");
            secondaryTextColor = colorOr(secondaryTextColor, "#C4CCCA");
            panelColor = colorOr(panelColor, "#E60C0C1C");
            lineColor = colorOr(lineColor, accentColor);
        }

        private static String valueOr(String value, String fallback) {
            return value == null ? fallback : value;
        }

        private static String colorOr(String value, String fallback) {
            if (value == null || !value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                return fallback;
            }
            return value;
        }

        public int backgroundTint() {
            return parseColor(backgroundTint, 0xA6000000);
        }

        public int accentColor() {
            return parseColor(accentColor, 0xFF59D6A3);
        }

        public int dimensionLabelColor() {
            return parseColor(dimensionLabelColor, accentColor());
        }

        public int tipLabelColor() {
            return parseColor(tipLabelColor, accentColor());
        }

        public int primaryTextColor() {
            return parseColor(primaryTextColor, 0xFFFFFFFF);
        }

        public int secondaryTextColor() {
            return parseColor(secondaryTextColor, 0xFFC4CCCA);
        }

        public int panelColor() {
            return parseColor(panelColor, 0xE60C0C1C);
        }

        public int lineColor() {
            return parseColor(lineColor, 0x70809090);
        }

        private static int parseColor(String value, int fallback) {
            if (value == null || !value.startsWith("#")) {
                return fallback;
            }
            try {
                long parsed = Long.parseLong(value.substring(1), 16);
                if (value.length() == 7) {
                    parsed |= 0xFF000000L;
                }
                return (int) parsed;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }
}
