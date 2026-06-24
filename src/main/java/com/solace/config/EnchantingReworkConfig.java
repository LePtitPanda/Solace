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
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class EnchantingReworkConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("solace-enchanting-rework.json");

    private static ConfigData data;

    private EnchantingReworkConfig() {
    }

    public static synchronized void init() {
        load();
    }

    public static int enchantingPowerRequiredForLevel(int level) {
        return arrayValue(load().enchantingLevelBookshelfPower, Math.max(0, level - 1));
    }

    public static int rerollPowerRequiredForButton(int buttonId) {
        if (buttonId == -1) {
            return 0;
        }

        int positions = 3;
        int mainSlot = Math.floorDiv(Math.max(0, buttonId), positions);
        int choiceSlot = Math.floorMod(Math.max(0, buttonId), positions);
        return powerRequiredForSlotChoice(mainSlot, choiceSlot);
    }

    public static int rerollPowerRequiredForMainSlot(int slotIndex) {
        return arrayValue(load().atoningMainSlotBookshelfPower, Math.max(0, slotIndex));
    }

    public static int[] slotChoicesForPower(int bookshelfPower) {
        ConfigData config = load();
        int mainSlots = unlockedCount(config.atoningMainSlotBookshelfPower, bookshelfPower);
        int choicesPerSlot = unlockedCount(config.atoningChoiceBookshelfPower, bookshelfPower);

        int[] choices = {0, 0, 0};
        for (int i = 0; i < choices.length && i < mainSlots; i++) {
            choices[i] = Math.min(3, choicesPerSlot);
        }
        return choices;
    }

    private static int unlockedCount(int[] thresholds, int bookshelfPower) {
        int count = 0;
        for (int threshold : thresholds) {
            if (bookshelfPower >= threshold) {
                count++;
            }
        }
        return Math.max(0, Math.min(3, count));
    }

    private static int powerRequiredForSlotChoice(int mainSlot, int choiceSlot) {
        ConfigData config = load();
        int mainSlotPower = arrayValue(config.atoningMainSlotBookshelfPower, Math.max(0, mainSlot));
        int choicePower = arrayValue(config.atoningChoiceBookshelfPower, Math.max(0, choiceSlot));
        return Math.max(mainSlotPower, choicePower);
    }

    public static int xpCostReductionPerShelf() {
        return Math.max(0, load().xpCostReductionPerShelf);
    }

    public static int maxXpCostReduction() {
        return Math.max(0, load().maxXpCostReduction);
    }

    public static List<ResourceLocation> xpCostReductionBookshelves() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String key : load().xpCostReductionBookshelves) {
            try {
                ids.add(new ResourceLocation(key));
            } catch (RuntimeException e) {
                LOGGER.warn("[Solace] Ignoring invalid enchanting rework bookshelf id {}", key);
            }
        }
        return Collections.unmodifiableList(ids);
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

    private static int arrayValue(int[] values, int index) {
        if (values.length == 0) {
            return 0;
        }
        if (index < values.length) {
            return Math.max(0, values[index]);
        }
        return Math.max(0, values[values.length - 1]);
    }

    private static final class ConfigData {
        public int[] enchantingLevelBookshelfPower;
        public int[] atoningMainSlotBookshelfPower;
        public int[] atoningChoiceBookshelfPower;
        @Deprecated
        public int[] slotChoiceBookshelfPower;
        public List<String> xpCostReductionBookshelves = new ArrayList<>(List.of("minecraft:chiseled_bookshelf"));
        public int xpCostReductionPerShelf = 1;
        public int maxXpCostReduction = 5;

        private boolean sanitize() {
            boolean changed = false;
            if (enchantingLevelBookshelfPower == null || enchantingLevelBookshelfPower.length == 0) {
                enchantingLevelBookshelfPower = new int[]{0, 3, 6, 9, 12};
                changed = true;
            }
            if (atoningMainSlotBookshelfPower == null || atoningMainSlotBookshelfPower.length == 0) {
                atoningMainSlotBookshelfPower = new int[]{0, 6, 12};
                changed = true;
            }
            if (atoningChoiceBookshelfPower == null || atoningChoiceBookshelfPower.length == 0) {
                atoningChoiceBookshelfPower = legacyChoiceThresholds();
                changed = true;
            }
            if (slotChoiceBookshelfPower != null) {
                slotChoiceBookshelfPower = null;
                changed = true;
            }
            if (xpCostReductionBookshelves == null) {
                xpCostReductionBookshelves = new ArrayList<>();
                changed = true;
            }
            if (xpCostReductionPerShelf < 0) {
                xpCostReductionPerShelf = 0;
                changed = true;
            }
            if (maxXpCostReduction < 0) {
                maxXpCostReduction = 0;
                changed = true;
            }
            return changed;
        }

        private int[] legacyChoiceThresholds() {
            if (slotChoiceBookshelfPower == null || slotChoiceBookshelfPower.length < 3) {
                return new int[]{0, 4, 8};
            }
            return new int[]{
                    Math.max(0, slotChoiceBookshelfPower[0]),
                    Math.max(0, slotChoiceBookshelfPower[1]),
                    Math.max(0, slotChoiceBookshelfPower[2])
            };
        }
    }
}
