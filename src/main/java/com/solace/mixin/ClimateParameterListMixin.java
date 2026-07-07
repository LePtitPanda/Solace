package com.solace.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(Climate.ParameterList.class)
public abstract class ClimateParameterListMixin<T> {
    private static final long MIN_VALID_CLIMATE_VALUE = Climate.quantizeCoord(-2.0F);
    private static final long MAX_VALID_CLIMATE_VALUE = Climate.quantizeCoord(2.0F);
    private static final Set<String> SPAWN_ISLAND_BIOMES = Set.of(
            "cold_island",
            "dodo_island",
            "sandy_island",
            "tide_pool",
            "tropical_island",
            "volcanic_island"
    );

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static <T> List<Pair<Climate.ParameterPoint, T>> solace$removeInvalidSpawnIslandClimateEntries(
            List<Pair<Climate.ParameterPoint, T>> values
    ) {
        List<Pair<Climate.ParameterPoint, T>> filtered = null;

        for (Pair<Climate.ParameterPoint, T> pair : values) {
            if (solace$isInvalidSpawnIslandEntry(pair)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(values.size());
                    for (Pair<Climate.ParameterPoint, T> previous : values) {
                        if (previous == pair) {
                            break;
                        }
                        filtered.add(previous);
                    }
                }
                continue;
            }

            if (filtered != null) {
                filtered.add(pair);
            }
        }

        return filtered == null ? values : filtered;
    }

    private static boolean solace$isInvalidSpawnIslandEntry(Pair<Climate.ParameterPoint, ?> pair) {
        return solace$isSpawnIslandBiome(pair.getSecond()) && solace$hasInvalidClimateValue(pair.getFirst());
    }

    private static boolean solace$isSpawnIslandBiome(Object value) {
        if (!(value instanceof Holder<?> holder)) {
            return false;
        }

        return holder.unwrapKey()
                .map(key -> key.location())
                .filter(id -> "spawn".equals(id.getNamespace()))
                .map(ResourceLocation::getPath)
                .filter(SPAWN_ISLAND_BIOMES::contains)
                .isPresent();
    }

    private static boolean solace$hasInvalidClimateValue(Climate.ParameterPoint point) {
        return solace$isInvalidClimateValue(point.temperature())
                || solace$isInvalidClimateValue(point.humidity())
                || solace$isInvalidClimateValue(point.continentalness())
                || solace$isInvalidClimateValue(point.erosion())
                || solace$isInvalidClimateValue(point.depth())
                || solace$isInvalidClimateValue(point.weirdness());
    }

    private static boolean solace$isInvalidClimateValue(Climate.Parameter parameter) {
        return parameter.min() < MIN_VALID_CLIMATE_VALUE || parameter.max() > MAX_VALID_CLIMATE_VALUE;
    }
}
