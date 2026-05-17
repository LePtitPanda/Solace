package com.solace.client;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public final class RuntimeDebugLoggingForcer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ENABLED_PROPERTY = "solace.forceDebugLogging";

    private static boolean applied;

    private RuntimeDebugLoggingForcer() {
    }

    public static void apply() {
        if (applied) {
            return;
        }
        applied = true;

        if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            LOGGER.info("[Solace] Runtime debug logging forcer disabled.");
            return;
        }

        System.setProperty("forge.logging.console.level", "debug");
        Configurator.setRootLevel(Level.DEBUG);
        LOGGER.info("[Solace] Runtime debug logging level forced to DEBUG.");
    }
}
