package com.solace.mixin;

import com.breakinblocks.justtrialspawners.client.model.AppearanceModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Just Trial Spawners wraps its trial spawner and vault block models in ModelEvent.ModifyBakingResult
 * with {@code forEach} and {@code replaceAll} over the whole model registry. With ModernFix dynamic
 * resources on, the registry bakes models lazily, so touching every value bakes every model in the
 * pack: about 24 s per launch and per resource reload (logged by Forge's event timing).
 *
 * This keeps the same result but scans only the keys, and reads and replaces just the mod's own
 * entries. Same filters and the same AppearanceModel arguments as the original (MIT, BreakinBlocks).
 */
@Mixin(value = AppearanceModel.class, remap = false)
public abstract class JustTrialSpawnersModelWrapMixin {
    /**
     * @author Solace
     * @reason Avoid baking every model in the pack through forEach/replaceAll on a lazy registry.
     */
    @Overwrite
    public static void wrapModels(Map<ResourceLocation, BakedModel> registry, @Nullable BakedModel missing) {
        List<ResourceLocation> appearanceIds = new ArrayList<>();
        List<ModelResourceLocation> targets = new ArrayList<>();
        for (ResourceLocation id : registry.keySet()) {
            if (id.getPath().startsWith("justtrialspawners/")) {
                appearanceIds.add(id);
            }
            if (id instanceof ModelResourceLocation location
                    && id.getNamespace().equals("justtrialspawners")
                    && !location.getVariant().equals("inventory")
                    && (id.getPath().equals("vault") || id.getPath().equals("trial_spawner"))) {
                targets.add(location);
            }
        }

        Map<ResourceLocation, BakedModel> appearances = new HashMap<>();
        for (ResourceLocation id : appearanceIds) {
            BakedModel model = registry.get(id);
            if (model != null && model != missing) {
                appearances.put(id, model);
            }
        }
        Map<ResourceLocation, BakedModel> models = Map.copyOf(appearances);

        for (ModelResourceLocation location : targets) {
            BakedModel model = registry.get(location);
            boolean vault = location.getPath().equals("vault");
            Map<String, String> properties = new HashMap<>();
            for (String property : location.getVariant().split(",")) {
                String[] pair = property.split("=", 2);
                if (pair.length == 2) {
                    properties.put(pair[0], pair[1]);
                }
            }
            String state = properties.getOrDefault(vault ? "vault_state" : "trial_spawner_state", "inactive");
            int turns = switch (properties.getOrDefault("facing", "north")) {
                case "east" -> 1;
                case "south" -> 2;
                case "west" -> 3;
                default -> 0;
            };
            registry.put(location, new AppearanceModel(model, models, state,
                    Boolean.parseBoolean(properties.get("ominous")), turns));
        }
    }
}
