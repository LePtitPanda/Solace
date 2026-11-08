package com.solace.client.setup;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Writes a {@link QualityPreset} into vanilla options, Embeddium, Oculus, the resource
 * pack list and the visual and performance mod configs of Astralis.
 *
 * <p>Quality matches the pack author's own settings (with 12/8 chunks). Vanilla,
 * Embeddium, Oculus and resource packs change immediately; Forge TOML configs are
 * hot-reloaded by Forge's file watcher; the remaining JSON/properties configs are read
 * by their mods at startup, so they apply on the next launch.
 */
public final class QualityPresetApplier {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Resource packs (beyond built-in and mod packs) that stay on with Performance. */
    private static final Set<String> PERFORMANCE_KEPT_PACKS = Set.of(
            "file/Fast Better Grass.zip",
            "better_grass_slabs",
            "file/GUI-SimpleStylized_5.3-1.20+.zip",
            "file/qrafty's-capitalized-font.zip");

    /** Every mod config line a preset writes, as (Performance, Balanced, Quality). */
    private static final List<ConfigEdit> EDITS = List.of(
            // Dynamic lights
            key("sodiumdynamiclights-client.toml", "mode", "\"SLOW\"", "\"FAST\"", "\"REALTIME\""),
            key("sodiumdynamiclights-client.toml", "block_entities", "false", "true", "true"),
            // Sodium Extras: chunk fade and distance culling of entities / block entities
            key("sodiumextras-client.toml", "chunkFadeSpeed", "\"OFF\"", "\"FAST\"", "\"SLOW\""),
            keyAfter("sodiumextras-client.toml", "distanceCulling.tileEntities]", "enable", "true", "false", "false"),
            keyAfter("sodiumextras-client.toml", "distanceCulling.entities]", "enable", "true", "false", "false"),
            // Particle Core
            key("particle_core_config.toml", "maxParticlesPerSheet", "4096", "8192", "16384"),
            key("particle_core_config.toml", "particleRenderDistanceMultiplier", "0.6", "0.85", "1.0"),
            // Culling and entity LOD
            json("entityculling.json", "tracingDistance", "64", "96", "128"),
            json("immersive_optimization.json", "minDistance", "4", "5", "6"),
            json("immersive_optimization.json", "blocksPerLevel", "40", "52", "64"),
            json("skinlayers.json", "renderDistanceLOD", "6", "10", "14"),
            // Ambient visuals
            key("cavedust.toml", "caveDustEnabled", "false", "true", "true"),
            key("immersive_weathering-client.toml", "falling_leaf_particles", "false", "true", "true"),
            key("immersive_weathering-client.toml", "leaves_decay_particles", "false", "true", "true"),
            key("subtle_effects/environment.toml", "firefliesEnabled", "false", "true", "true"),
            key("subtle_effects/environment.toml", "smallWaterfallsEnabled", "false", "true", "true"),
            key("subtle_effects/environment.toml", "largeWaterfallParticleDensity", "0.4", "0.7", "1.0"),
            key("subtle_effects/biomes.toml", "fireflyDensity", "2", "4", "6"),
            key("subtle_effects/biomes.toml", "pollenDensity", "15", "30", "50"),
            key("subtle_effects/blocks.toml", "rainWaterRipplesDensity", "0.1", "0.25", "0.35"),
            // Streams Reflowing water particles
            key("streamsreflowing-client.properties", "particleDensity", "0.2", "0.35", "0.5"),
            key("streamsreflowing-client.properties", "maxStreamParticlesPerTick", "60", "120", "200"),
            key("streamsreflowing-client.properties", "streamParticleScanRadius", "32", "48", "64"),
            key("streamsreflowing-client.properties", "streamBubbles", "0", "1", "1"),
            key("streamsreflowing-client.properties", "waterfallFoam", "1", "2", "3"),
            key("streamsreflowing-client.properties", "streamDebris", "false", "true", "true"),
            // Sound Physics Remastered
            key("sound_physics_remastered/soundphysics.properties", "environment_evaluation_ray_count", "8", "16", "32"),
            key("sound_physics_remastered/soundphysics.properties", "environment_evaluation_ray_bounces", "1", "2", "4"),
            key("sound_physics_remastered/soundphysics.properties", "max_occlusion_rays", "4", "8", "16"));

    private QualityPresetApplier() {
    }

    public static void apply(QualityPreset preset) {
        Minecraft minecraft = Minecraft.getInstance();
        applyVanilla(minecraft.options, preset);

        if (ModList.get().isLoaded("embeddium")) {
            try {
                EmbeddiumPresetBridge.apply(preset);
            } catch (Throwable t) {
                LOGGER.warn("[Solace] Could not apply Embeddium settings for preset {}.", preset.id(), t);
            }
        }
        if (preset == QualityPreset.PERFORMANCE) {
            disableShaders();
        }

        Path config = FMLPaths.CONFIGDIR.get();
        for (ConfigEdit edit : EDITS) {
            edit.apply(config, preset);
        }

        if (minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
        SolaceClientSettings.setQualityPreset(preset);
        LOGGER.info("[Solace] Applied quality preset {}.", preset.id());

        // Last: a pack change starts a resource reload.
        applyResourcePacks(minecraft, preset);
    }

    private static void applyVanilla(Options options, QualityPreset preset) {
        options.renderDistance().set(preset.pick(8, 10, 12));
        options.simulationDistance().set(preset.pick(5, 6, 8));
        options.particles().set(preset.pick(ParticleStatus.DECREASED, ParticleStatus.ALL, ParticleStatus.ALL));
        options.entityShadows().set(preset.pick(false, true, true));
        options.graphicsMode().set(GraphicsStatus.FAST);
        options.entityDistanceScaling().set(preset.pick(0.75D, 1.0D, 1.0D));
        options.framerateLimit().set(preset.pick(120, 260, 260));
        options.biomeBlendRadius().set(preset.pick(0, 1, 2));
        options.cloudStatus().set(preset.pick(CloudStatus.OFF, CloudStatus.FAST, CloudStatus.FANCY));
        options.ambientOcclusion().set(true);
        options.save();
    }

    /** Turns Oculus shaders off now (if loaded) and in its config for the next launch. */
    private static void disableShaders() {
        setLine(FMLPaths.CONFIGDIR.get().resolve("oculus.properties"), null, "enableShaders", "false", false);
        if (!ModList.get().isLoaded("oculus")) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if ((boolean) apiClass.getMethod("isShaderPackInUse").invoke(api)) {
                Object config = apiClass.getMethod("getConfig").invoke(api);
                Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig")
                        .getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, false);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("[Solace] Could not turn off shaders through the Oculus API.", e);
        }
    }

    /**
     * Performance turns off every user resource pack except a short list; built-in and
     * mod packs are never touched. The previous list is remembered so Balanced and
     * Quality can restore it in its original order.
     */
    private static void applyResourcePacks(Minecraft minecraft, QualityPreset preset) {
        try {
            PackRepository repository = minecraft.getResourcePackRepository();
            List<String> current = new ArrayList<>(repository.getSelectedIds());
            List<String> target;
            if (preset == QualityPreset.PERFORMANCE) {
                target = new ArrayList<>();
                for (String id : current) {
                    if (!id.startsWith("file/") || PERFORMANCE_KEPT_PACKS.contains(id)) {
                        target.add(id);
                    }
                }
                if (!target.equals(current) && SolaceClientSettings.getPacksBeforePerformance() == null) {
                    SolaceClientSettings.setPacksBeforePerformance(current);
                }
            } else {
                List<String> saved = SolaceClientSettings.getPacksBeforePerformance();
                if (saved == null) {
                    return;
                }
                Set<String> available = new LinkedHashSet<>(repository.getAvailableIds());
                LinkedHashSet<String> merged = new LinkedHashSet<>();
                for (String id : saved) {
                    if (available.contains(id)) {
                        merged.add(id);
                    }
                }
                merged.addAll(current);
                target = new ArrayList<>(merged);
                SolaceClientSettings.setPacksBeforePerformance(null);
            }
            if (!target.equals(current)) {
                repository.setSelected(target);
                minecraft.options.updateResourcePacks(repository);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("[Solace] Could not update resource packs for preset {}.", preset.id(), e);
        }
    }

    private static ConfigEdit key(String file, String key, String performance, String balanced, String quality) {
        return new ConfigEdit(file, null, key, false, performance, balanced, quality);
    }

    private static ConfigEdit keyAfter(String file, String anchor, String key, String performance, String balanced,
                                       String quality) {
        return new ConfigEdit(file, anchor, key, false, performance, balanced, quality);
    }

    private static ConfigEdit json(String file, String key, String performance, String balanced, String quality) {
        return new ConfigEdit(file, null, key, true, performance, balanced, quality);
    }

    private record ConfigEdit(String file, String anchor, String key, boolean json,
                              String performance, String balanced, String quality) {
        void apply(Path configDir, QualityPreset preset) {
            setLine(configDir.resolve(file), anchor, key, preset.pick(performance, balanced, quality), json);
        }
    }

    /**
     * Replaces the value of the first {@code key = value} (or JSON {@code "key": value})
     * line after {@code anchor}, keeping comments, spacing and line endings.
     */
    private static void setLine(Path file, String anchor, String key, String value, boolean json) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Pattern pattern = json
                ? Pattern.compile("^([ \\t]*\"" + Pattern.quote(key) + "\"[ \\t]*:[ \\t]*)[^,\\r\\n]*", Pattern.MULTILINE)
                : Pattern.compile("^([ \\t]*" + Pattern.quote(key) + "[ \\t]*=[ \\t]*)[^\\r\\n]*", Pattern.MULTILINE);
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int from = 0;
            if (anchor != null) {
                from = content.indexOf(anchor);
                if (from < 0) {
                    LOGGER.debug("[Solace] Preset anchor {} not found in {}.", anchor, file);
                    return;
                }
            }
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find(from)) {
                LOGGER.debug("[Solace] Preset key {} not found in {}.", key, file);
                return;
            }
            String updated = content.substring(0, matcher.start()) + matcher.group(1) + value
                    + content.substring(matcher.end());
            if (!updated.equals(content)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Solace] Could not update {} for the quality preset.", file, e);
        }
    }
}
