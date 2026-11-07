package com.solace.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * EMI 1.1.24 added {@code GlobalMixin}: an empty @Pseudo mixin targeting every EMI class so EMI's
 * plugin can log which EMI classes other mods mix into (EmiMixinTransformation.exposeSinners/preach).
 * Connector Extras' EMI bridge loads {@code dev.emi.emi.api.EmiPlugin} while Connector builds the game
 * layer, before mixins are prepared, so the required EMI config fails with
 * MixinTargetAlreadyLoadedException and the game cannot start. Dropping the diagnostic-only marker
 * mixin restores EMI 1.1.22 behaviour; EMI's functional mixins are untouched.
 *
 * Runs from Solace's plugin onLoad. Mixin selects every config (removing it from the registry and
 * queueing it on the processor) before preparing any of them, so EMI's config is found either still
 * registered or in the processor's pending list.
 */
final class EmiGlobalMixinGuard {
    private static final Logger LOGGER = LogManager.getLogger("Solace/EmiCompat");
    private static final String EMI_CONFIG = "emi.mixins.json";
    private static final String MARKER = "GlobalMixin";

    private EmiGlobalMixinGuard() { }

    static void apply() {
        try {
            List<Object> candidates = new ArrayList<>();
            for (Config config : Mixins.getConfigs()) candidates.add(config.getConfig());
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer != null) {
                for (Object processor : fieldValues(transformer)) {
                    if (processor == null || !processor.getClass().getName().endsWith("MixinProcessor")) continue;
                    for (Object value : fieldValues(processor)) {
                        if (value instanceof Collection<?> collection) candidates.addAll(collection);
                    }
                }
            }
            for (Object candidate : candidates) {
                if (candidate instanceof IMixinConfig config && EMI_CONFIG.equals(config.getName())) {
                    if (strip(candidate)) {
                        LOGGER.info("Skipped EMI diagnostic {} (conflicts with Connector Extras' early EMI bridge loading)", MARKER);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not inspect EMI mixin config; EMI may fail to start alongside Connector Extras", t);
        }
    }

    private static boolean strip(Object config) throws IllegalAccessException {
        boolean removed = false;
        for (Object value : fieldValues(config)) {
            if (value instanceof List<?> list && list.contains(MARKER)) {
                removed |= list.removeIf(MARKER::equals);
            }
        }
        return removed;
    }

    private static List<Object> fieldValues(Object owner) throws IllegalAccessException {
        List<Object> values = new ArrayList<>();
        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                } catch (RuntimeException inaccessible) {
                    continue;
                }
                values.add(field.get(owner));
            }
        }
        return values;
    }
}
