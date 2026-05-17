package com.solace.client;

import com.solace.Solace;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientStartupTimingStabilizer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ENABLED_PROPERTY = "solace.startupTimingStabilizer";
    private static final String DELAY_PROPERTY = "solace.startupTimingDelayMillis";
    private static final int DEFAULT_DELAY_MILLIS = 2500;

    private ClientStartupTimingStabilizer() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            LOGGER.info("[Solace] Client startup timing stabilizer disabled.");
            return;
        }

        event.enqueueWork(() -> {
            int delayMillis = readDelayMillis();
            if (delayMillis <= 0) {
                LOGGER.info("[Solace] Client startup timing stabilizer skipped because delay is {} ms.", delayMillis);
                return;
            }

            LOGGER.info("[Solace] Waiting {} ms during client startup to stabilize resource reload timing.", delayMillis);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("[Solace] Client startup timing stabilizer was interrupted.", e);
            }
        });
    }

    private static int readDelayMillis() {
        String rawValue = System.getProperty(DELAY_PROPERTY, Integer.toString(DEFAULT_DELAY_MILLIS));
        try {
            int parsed = Integer.parseInt(rawValue);
            return Math.max(0, Math.min(parsed, 10000));
        } catch (NumberFormatException e) {
            LOGGER.warn("[Solace] Invalid {} value '{}'; using {} ms.", DELAY_PROPERTY, rawValue, DEFAULT_DELAY_MILLIS);
            return DEFAULT_DELAY_MILLIS;
        }
    }
}
