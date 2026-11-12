package com.solace.client.quests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/solace-quests-client.json. Missing or broken file = defaults. */
public final class QuestClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("solace-quests-client.json");
    private static QuestClientConfig instance;

    /** Show the tracker on the HUD (also toggled by its keybind). */
    public boolean hud = true;
    /** "left" or "right" edge of the screen. */
    public String hudSide = "right";
    /** Distance from the top, as a fraction of the screen height. */
    public double hudTop = 0.25;
    public boolean toasts = true;
    /** Arrow and distance to the tracked quest's marker in the HUD tracker. */
    public boolean compass = true;
    /** Mirror quest markers as temporary Xaero's Minimap waypoints. */
    public boolean xaeroWaypoints = true;
    /** In-world beams for markers that ask for one. */
    public boolean beams = true;
    /** Right-clicking this item opens the quest book, e.g. "minecraft:book". Empty = keybind only. */
    public String bookItem = "";

    public static QuestClientConfig get() {
        if (instance == null) {
            instance = new QuestClientConfig();
            try {
                if (Files.isRegularFile(PATH)) {
                    QuestClientConfig read = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8),
                            QuestClientConfig.class);
                    if (read != null) {
                        instance = read;
                    }
                } else {
                    instance.save();
                }
            } catch (Exception exception) {
                LOGGER.error("[Solace Quests] Could not read {}; using defaults", PATH, exception);
            }
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            LOGGER.error("[Solace Quests] Could not save {}", PATH, exception);
        }
    }
}
