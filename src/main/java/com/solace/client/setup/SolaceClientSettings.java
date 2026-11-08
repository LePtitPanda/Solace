package com.solace.client.setup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-player client state for the first-launch setup.
 *
 * <p>Stored in the game directory rather than {@code config/}: the launcher manages
 * {@code config/}, so a file there would ship the pack author's own "already done"
 * state to every player.
 */
public final class SolaceClientSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.GAMEDIR.get().resolve("solace-client.json");

    private static Data data;

    private SolaceClientSettings() {
    }

    public static synchronized boolean isFirstLaunchComplete() {
        return load().firstLaunchComplete;
    }

    public static synchronized void markFirstLaunchComplete() {
        load().firstLaunchComplete = true;
        save();
    }

    /** The last preset the player applied, or {@code null} if they never picked one. */
    public static synchronized QualityPreset getQualityPreset() {
        return QualityPreset.byId(load().qualityPreset);
    }

    public static synchronized void setQualityPreset(QualityPreset preset) {
        load().qualityPreset = preset.id();
        save();
    }

    /** Resource packs selected before Performance trimmed them, or {@code null}. */
    public static synchronized java.util.List<String> getPacksBeforePerformance() {
        java.util.List<String> packs = load().packsBeforePerformance;
        return packs == null ? null : java.util.List.copyOf(packs);
    }

    public static synchronized void setPacksBeforePerformance(java.util.List<String> packs) {
        load().packsBeforePerformance = packs == null ? null : new java.util.ArrayList<>(packs);
        save();
    }

    private static Data load() {
        if (data != null) {
            return data;
        }
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                data = GSON.fromJson(reader, Data.class);
            } catch (IOException | JsonParseException | IllegalStateException e) {
                LOGGER.warn("[Solace] Could not read {}; first-launch setup will run again.", PATH, e);
            }
        }
        if (data == null) {
            data = new Data();
        }
        return data;
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.warn("[Solace] Could not save {}.", PATH, e);
        }
    }

    private static final class Data {
        boolean firstLaunchComplete;
        String qualityPreset;
        java.util.List<String> packsBeforePerformance;
    }
}
