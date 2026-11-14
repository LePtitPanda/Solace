package com.solace.config;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/** Every Solace config lives in config/solace/. Files left at their old config/solace-&lt;name&gt; path are moved on first use. */
public final class SolaceConfigPaths {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SolaceConfigPaths() {
    }

    /** config/solace/&lt;name&gt;, migrated from config/solace-&lt;name&gt;. */
    public static Path resolve(String name) {
        return resolve(name, FMLPaths.CONFIGDIR.get().resolve("solace-" + name));
    }

    /** config/solace/&lt;name&gt;, migrated from {@code legacy} (a file or a folder). */
    public static Path resolve(String name, Path legacy) {
        Path path = dir().resolve(name);
        if (Files.exists(legacy) && !Files.exists(path)) {
            try {
                Files.createDirectories(path.getParent());
                Files.move(legacy, path);
                LOGGER.info("[Solace] Moved {} to {}", legacy, path);
            } catch (IOException e) {
                LOGGER.warn("[Solace] Could not move {} to {}; it will be recreated there.", legacy, path, e);
            }
        }
        return path;
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("solace");
    }
}
