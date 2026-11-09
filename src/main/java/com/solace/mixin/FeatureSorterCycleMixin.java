package com.solace.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.solace.worldgen.FeatureCycleResolver;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.function.Function;

@Mixin(FeatureSorter.class)
public abstract class FeatureSorterCycleMixin {
    @WrapMethod(method = "buildFeaturesPerStep(Ljava/util/List;Ljava/util/function/Function;Z)Ljava/util/List;")
    private static <T> List<FeatureSorter.StepFeatureData> solace$resolveCycles(
            List<T> sources, Function<T, List<HolderSet<PlacedFeature>>> featureListGetter,
            boolean listInvolvedBiomes, Operation<List<FeatureSorter.StepFeatureData>> original) {
        return FeatureCycleResolver.withFallback(sources, featureListGetter,
                () -> original.call(sources, featureListGetter, listInvolvedBiomes));
    }
}
