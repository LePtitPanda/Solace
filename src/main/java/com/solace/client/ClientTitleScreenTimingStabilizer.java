package com.solace.client;

import com.solace.Solace;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT)
public final class ClientTitleScreenTimingStabilizer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ENABLED_PROPERTY = "solace.titleScreenTimingStabilizer";
    private static final String DELAY_PROPERTY = "solace.titleScreenTimingDelayMillis";
    private static final int DEFAULT_DELAY_MILLIS = 5000;

    private static boolean delayedTitleScreen;

    private ClientTitleScreenTimingStabilizer() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (delayedTitleScreen || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }

        delayedTitleScreen = true;
        if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            LOGGER.info("[Solace] Title screen timing stabilizer disabled.");
            return;
        }

        int delayMillis = readDelayMillis();
        if (delayMillis <= 0) {
            LOGGER.info("[Solace] Title screen timing stabilizer skipped because delay is {} ms.", delayMillis);
            return;
        }

        LOGGER.info("[Solace] Waiting {} ms before first title screen to stabilize final menu timing.", delayMillis);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[Solace] Title screen timing stabilizer was interrupted.", e);
        }
    }

    private static int readDelayMillis() {
        String rawValue = System.getProperty(DELAY_PROPERTY, Integer.toString(DEFAULT_DELAY_MILLIS));
        try {
            int parsed = Integer.parseInt(rawValue);
            return Math.max(0, Math.min(parsed, 15000));
        } catch (NumberFormatException e) {
            LOGGER.warn("[Solace] Invalid {} value '{}'; using {} ms.", DELAY_PROPERTY, rawValue, DEFAULT_DELAY_MILLIS);
            return DEFAULT_DELAY_MILLIS;
        }
    }
}
