package com.solace.compat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

/**
 * Integrated API normally shuts its locator pool down during
 * ServerStoppingEvent, before Minecraft serializes loaded villagers. A
 * villager map trade may be built while its NBT is written, so the executor
 * must remain available until ServerStoppedEvent.
 */
public final class IntegratedApiLocatorShutdownSupport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ASYNC_LOCATOR_CLASS = "com.craisinlord.integrated_api.utils.AsyncLocator";

    private IntegratedApiLocatorShutdownSupport() {
    }

    public static void shutdownAfterWorldSave() {
        if (!ModList.get().isLoaded("integrated_api")) {
            return;
        }

        try {
            Class<?> locatorClass = Class.forName(ASYNC_LOCATOR_CLASS);
            Field executorField = locatorClass.getDeclaredField("LOCATING_EXECUTOR_SERVICE");
            executorField.setAccessible(true);
            Object executor = executorField.get(null);
            if (executor instanceof ExecutorService service) {
                service.shutdown();
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("[Solace] Could not defer Integrated API's locator shutdown until after world save.", exception);
        }
    }
}
