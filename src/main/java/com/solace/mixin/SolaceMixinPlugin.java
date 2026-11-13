package com.solace.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.solace.compat.create6.DDCreate6SignatureExtension;
import com.solace.compat.radiance.RadianceCompatibilityExtension;
import com.solace.compat.performance.ClientPerformancePatches;
import com.solace.compat.performance.CommonPerformancePatches;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import com.solace.config.PerformanceConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Bootstrap compatibility fixes that must run before another mod's mixin. */
public final class SolaceMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Solace/Create6Compat");
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void onLoad(String mixinPackage) {
        EmiGlobalMixinGuard.apply();
        reenableImmediatelyFastHudBatching();
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixinTransformer
                && mixinTransformer.getExtensions() instanceof Extensions extensions) {
            extensions.add(new DDCreate6SignatureExtension());
            extensions.add(new RadianceCompatibilityExtension());
            LOGGER.info("Registered DD Create 6 compatibility before target mixin application");
        } else {
            throw new IllegalStateException("Solace could not register the DD Create 6 compatibility extension");
        }
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".PolymorphRecipeLockMixin")) {
            return hasClass("com/illusivesoulworks/polymorph/common/PolymorphForgePacketDistributor.class");
        }
        if (mixinClassName.endsWith(".FastBenchRecipeLockMixin")) {
            return hasClass("dev/shadowsoffire/fastbench/util/FastBenchUtil.class");
        }
        if (mixinClassName.contains(".Structurify")) {
            return hasClass("com/faboslav/structurify/common/Structurify.class");
        }
        if (mixinClassName.endsWith(".FeatureSorterCycleMixin")) {
            return CommonPerformancePatches.featureCycleResolver();
        }
        if (mixinClassName.endsWith(".NoiseChunkHashMemoMixin")) {
            return CommonPerformancePatches.noiseChunkHashMemo();
        }
        if (mixinClassName.contains(".chunkguards.")) {
            // ServerCore supplies these exact guards; defer to it if installed later.
            return !hasClass("me/wesley1808/servercore/mixin/optimizations/sync_loads/MapItemMixin.class");
        }
        if (mixinClassName.endsWith(".ImmediatelyFastHudBatchingMixin")) {
            return hasClass("net/raphimc/immediatelyfast/ImmediatelyFast.class")
                    && hasClass("net/raphimc/immediatelyfastapi/ImmediatelyFastApi.class")
                    && hasClass("com/lowdragmc/lowdraglib/client/renderer/IItemRendererProvider.class")
                    && ClientPerformancePatches.immediatelyFastHudBatching();
        }
        if (mixinClassName.endsWith(".CreateMountedStorageLookupMixin")) {
            return hasClass("com/simibubi/create/api/contraption/storage/item/MountedItemStorageWrapper.class")
                    && net.minecraftforge.fml.loading.FMLLoader.getLoadingModList().getMods().stream()
                    .anyMatch(mod -> mod.getModId().equals("create") && mod.getVersion().toString().equals("6.0.8"))
                    && PerformanceConfig.isCreateMountedStorageLookupEnabled();
        }
        if (mixinClassName.endsWith(".EarthWaterChunkScanMixin")) {
            return hasClass("potatowolfie/earth_and_water/EarthWater.class")
                    && PerformanceConfig.isEarthWaterSectionScanEnabled();
        }
        if (mixinClassName.endsWith(".FluidGameRulesMixin")) {
            return PerformanceConfig.isFluidGameRuleCacheEnabled();
        }
        if (mixinClassName.endsWith(".CrashReportCategoryMixin")) {
            // Crash Assistant 1.11+ redirects the same String.equals call with an equivalent null-safe check.
            return !hasClass("dev/kostromdan/mods/crash_assistant/common/mixin/CrashReportCategoryMixin.class");
        }
        if (mixinClassName.endsWith(".TfmgGoggleOverlayGuardMixin")) {
            return hasClass("com/drmangotea/tfmg/mixin/GoggleOverlayRendererMixin.class");
        }
        if (mixinClassName.endsWith(".XaeroMapWriteBudgetMixin")) {
            return hasClass("xaero/map/MapWriter.class") && ClientPerformancePatches.xaeroMapWriteBudget();
        }
        if (mixinClassName.endsWith(".SubtleEffectsBiomeLookupMixin")) {
            return hasClass("einstein/subtle_effects/ticking/BiomeEffectsManager.class")
                    && ClientPerformancePatches.ambientBiomeCells();
        }
        if (mixinClassName.endsWith(".JustTrialSpawnersModelWrapMixin")) {
            return hasClass("com/breakinblocks/justtrialspawners/client/model/AppearanceModel.class")
                    && ClientPerformancePatches.trialSpawnerModelWrap();
        }
        if (mixinClassName.endsWith(".FzzyConditionCacheMixin")) {
            return ClientPerformancePatches.fzzyConditionCache();
        }
        if (mixinClassName.endsWith(".DynamicFpsLoadingMixin")) {
            return ClientPerformancePatches.dynamicFpsLoading();
        }
        if (mixinClassName.endsWith(".EmbeddiumGraphStatsMixin")) {
            return Boolean.getBoolean("solace.graphStats");
        }
        if (mixinClassName.endsWith(".EmbeddiumVisibleSpriteCacheMixin")) {
            // Stats mode keeps the mixin applied with the cache off, to time Embeddium's original loop.
            return ClientPerformancePatches.visibleSpriteCache() || Boolean.getBoolean("solace.spriteTickStats");
        }
        if (mixinClassName.endsWith(".EmbeddiumSchedulerStatsMixin")
                || mixinClassName.endsWith(".EmbeddiumSortStatsMixin")) {
            return Boolean.getBoolean("solace.schedulerStats");
        }
        if (mixinClassName.endsWith(".EmbeddiumChunkBuilderAccessor")) {
            return Boolean.getBoolean("solace.schedulerStats");
        }
        if (mixinClassName.endsWith(".EmbeddiumMergedDrawsMixin")) {
            // Stats mode keeps the mixin applied with merging off, to count Embeddium's original commands.
            return ClientPerformancePatches.mergedDrawCommands() || Boolean.getBoolean("solace.drawStats");
        }
        if (mixinClassName.endsWith(".BannerPatternLodMixin")) {
            return ClientPerformancePatches.bannerPatternLod();
        }
        if (mixinClassName.endsWith(".ReactiveMusicIdleMixin")) {
            return ClientPerformancePatches.musicIdleWait();
        }
        if (mixinClassName.endsWith(".CatnipLightCacheMixin")) {
            return ClientPerformancePatches.lightCacheMissPositions();
        }
        if (mixinClassName.endsWith(".BossesRiseBannerMixin")
                || mixinClassName.endsWith(".BannerIndexChunkMixin")) {
            return ClientPerformancePatches.bannerIndex();
        }
        if (mixinClassName.endsWith(".GlidersUpdraftMixin")) {
            return ClientPerformancePatches.gliderBlockGate();
        }
        if (mixinClassName.endsWith(".SheepVariantReadMixin")
                || mixinClassName.endsWith(".ShearedWoolVariantMixin")) {
            return ClientPerformancePatches.sheepVariantRead();
        }
        if (mixinClassName.endsWith(".RadianceConnectorResourceMixin")) {
            return RadianceCompatibilityExtension.needsConnectorHook();
        }
        return true;
    }

    private static void reenableImmediatelyFastHudBatching() {
        try {
            if (!hasClass("net/raphimc/immediatelyfast/ImmediatelyFast.class")
                    || !ClientPerformancePatches.immediatelyFastHudBatching()) {
                return;
            }
            // Mixin 0.8.5 selects every config (plugin onLoad) before preparing any mixins.
            // IF consults config.hud_batching in shouldApplyMixin, then runtimeConfig while rendering.
            Class<?> immediatelyFast = Class.forName("net.raphimc.immediatelyfast.ImmediatelyFast");
            immediatelyFast.getMethod("earlyInit").invoke(null);

            Path path = FMLPaths.CONFIGDIR.get().resolve("immediatelyfast.json");
            if (!Files.isRegularFile(path)) return;
            try (Reader input = Files.newBufferedReader(path)) {
                JsonElement json = JsonParser.parseReader(input);
                JsonElement hudBatching = json.getAsJsonObject().get("hud_batching");
                if (hudBatching == null || !hudBatching.isJsonPrimitive()
                        || !hudBatching.getAsJsonPrimitive().isBoolean() || !hudBatching.getAsBoolean()) {
                    return;
                }
            }

            if (FMLLoader.getLoadingModList().getMods().stream()
                    .anyMatch(mod -> mod.getModId().equals("draconicevolution"))) {
                LOGGER.info("[Solace] Keeping ImmediatelyFast HUD batching unchanged for Draconic Evolution");
                return;
            }
            Object config = immediatelyFast.getField("config").get(null);
            if (config.getClass().getField("debug_only_and_not_recommended_disable_mod_conflict_handling")
                    .getBoolean(config)) {
                LOGGER.info("[Solace] Keeping ImmediatelyFast HUD batching unchanged with debug conflict handling disabled");
                return;
            }
            Object runtimeConfig = immediatelyFast.getField("runtimeConfig").get(null);
            Field configHudBatching = config.getClass().getField("hud_batching");
            Field runtimeHudBatching = runtimeConfig.getClass().getField("hud_batching");
            // Resolve and validate both fields before changing either configuration.
            configHudBatching.getBoolean(config);
            runtimeHudBatching.getBoolean(runtimeConfig);
            configHudBatching.setBoolean(config, true);
            runtimeHudBatching.setBoolean(runtimeConfig, true);
            LOGGER.info("[Solace] Re-enabled ImmediatelyFast HUD batching (MBD2 conflict fixed upstream in f8d2732)");
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("[Solace] Could not re-enable ImmediatelyFast HUD batching", exception);
        }
    }

    private static boolean hasClass(String resource) {
        try (java.io.InputStream in = org.spongepowered.asm.service.MixinService.getService().getResourceAsStream(resource)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
