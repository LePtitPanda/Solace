package com.solace.loot;

import com.solace.config.SolaceConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Modpack-global persistence for the in-game loot editor. */
public final class LootEditorConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = SolaceConfigPaths.resolve("loot-editor.json");
    private static JsonObject root = emptyRoot();

    private LootEditorConfig() {
    }

    public static synchronized void load() {
        if (!Files.isRegularFile(PATH)) {
            root = emptyRoot();
            return;
        }
        try {
            JsonObject parsed = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!parsed.has("replacements") || !parsed.get("replacements").isJsonObject()) {
                parsed.add("replacements", new JsonObject());
            }
            if (!parsed.has("overrides") || !parsed.get("overrides").isJsonObject()) {
                parsed.add("overrides", new JsonObject());
            }
            if (!parsed.has("suppressModifiers") || !parsed.get("suppressModifiers").isJsonObject()) {
                parsed.add("suppressModifiers", new JsonObject());
            }
            if (!parsed.has("extraRules") || !parsed.get("extraRules").isJsonObject()) {
                parsed.add("extraRules", new JsonObject());
            }
            if (!parsed.has("observedExtras") || !parsed.get("observedExtras").isJsonObject()) {
                parsed.add("observedExtras", new JsonObject());
            }
            if (!parsed.has("extraDetector") || !parsed.get("extraDetector").isJsonObject()) {
                parsed.add("extraDetector", new JsonObject());
            }
            root = parsed;
            LootEditorManager.clearCache();
            LootExtraManager.clearDiscoveryCache();
        } catch (Exception exception) {
            LOGGER.error("[Solace] Could not read {}. Keeping the previous loot editor configuration.", PATH, exception);
        }
    }

    public static synchronized JsonObject replacements() {
        return root.getAsJsonObject("replacements");
    }

    public static synchronized JsonObject overrides() {
        return root.getAsJsonObject("overrides");
    }

    public static synchronized JsonObject suppressModifiers() {
        return root.getAsJsonObject("suppressModifiers");
    }

    public static synchronized JsonObject extraRules() {
        return root.getAsJsonObject("extraRules");
    }

    public static synchronized JsonObject observedExtras() {
        return root.getAsJsonObject("observedExtras");
    }

    public static synchronized JsonObject extraDetector() {
        return root.getAsJsonObject("extraDetector");
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("[Solace] Could not save loot editor configuration to {}", PATH, exception);
        }
    }

    private static JsonObject emptyRoot() {
        JsonObject object = new JsonObject();
        object.addProperty("format", 1);
        object.add("replacements", new JsonObject());
        object.add("overrides", new JsonObject());
        object.add("suppressModifiers", new JsonObject());
        object.add("extraRules", new JsonObject());
        object.add("observedExtras", new JsonObject());
        object.add("extraDetector", new JsonObject());
        return object;
    }
}
