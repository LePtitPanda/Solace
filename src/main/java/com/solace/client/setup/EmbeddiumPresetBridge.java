package com.solace.client.setup;

import java.io.IOException;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions.GraphicsQuality;

/** Kept in its own class so Embeddium types only load when Embeddium is present. */
final class EmbeddiumPresetBridge {
    private EmbeddiumPresetBridge() {
    }

    static void apply(QualityPreset preset) throws IOException {
        SodiumGameOptions options = SodiumClientMod.options();
        options.quality.leavesQuality = preset.pick(GraphicsQuality.FAST, GraphicsQuality.FANCY, GraphicsQuality.FANCY);
        options.quality.weatherQuality = preset.pick(GraphicsQuality.FAST, GraphicsQuality.DEFAULT,
                GraphicsQuality.DEFAULT);
        options.quality.enableVignette = preset.pick(false, true, true);
        SodiumGameOptions.writeToDisk(options);
    }
}
