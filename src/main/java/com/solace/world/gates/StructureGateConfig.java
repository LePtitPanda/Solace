package com.solace.world.gates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * config/solace-structure-gates.json: which structures are gated and their default mode. No structure
 * id lives in Java; the shipped default is an empty list and the modpack's copy holds the real one.
 */
public final class StructureGateConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("solace-structure-gates.json");

    public enum Mode {
        ONCE, INFINITE;

        public static Mode parse(String text) {
            if (text == null) {
                return null;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One line of the file. {@code tag} entries are expanded against the structure registry later. */
    public record Entry(boolean tag, ResourceLocation id, Mode mode, boolean initiallyOpen) {
        public String key() {
            return (tag ? "#" : "") + id;
        }
    }

    private static final class FileData {
        int version = 1;
        List<RawEntry> gates = new ArrayList<>();
    }

    private static final class RawEntry {
        String structure;
        String mode;
        @SerializedName("initially_open")
        Boolean initiallyOpen;
    }

    private StructureGateConfig() {
    }

    /** Reads the file (writing an empty one if missing). Bad lines are logged and skipped. */
    static List<Entry> load() {
        FileData file = null;
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                file = GSON.fromJson(reader, FileData.class);
            } catch (IOException | JsonParseException exception) {
                LOGGER.error("[Solace] Could not read {}; no structure is gated until it is fixed.", PATH, exception);
                return List.of();
            }
        } else {
            file = new FileData();
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(file, writer);
            } catch (IOException exception) {
                LOGGER.warn("[Solace] Could not write default {}", PATH, exception);
            }
        }
        List<Entry> entries = new ArrayList<>();
        if (file == null || file.gates == null) {
            return entries;
        }
        for (RawEntry raw : file.gates) {
            if (raw == null || raw.structure == null || raw.structure.isBlank()) {
                LOGGER.warn("[Solace] {}: gate entry without \"structure\" ignored", PATH.getFileName());
                continue;
            }
            String text = raw.structure.trim();
            boolean tag = text.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? text.substring(1) : text);
            if (id == null) {
                LOGGER.warn("[Solace] {}: \"{}\" is not a valid structure id or #tag; ignored",
                        PATH.getFileName(), text);
                continue;
            }
            Mode mode = raw.mode == null ? Mode.INFINITE : Mode.parse(raw.mode);
            if (mode == null) {
                LOGGER.warn("[Solace] {}: \"{}\" has mode \"{}\" (expected once or infinite); using infinite",
                        PATH.getFileName(), text, raw.mode);
                mode = Mode.INFINITE;
            }
            entries.add(new Entry(tag, id, mode, Boolean.TRUE.equals(raw.initiallyOpen)));
        }
        LOGGER.info("[Solace] Loaded {} structure gate entr{} from {}", entries.size(),
                entries.size() == 1 ? "y" : "ies", PATH.getFileName());
        return entries;
    }
}
