package com.solace.performance;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Map;

/**
 * Equality-keyed wrapping with bounded, weak-identity memoization of stable function hashes.
 * Vanilla record components and identity-hashed sampling wrappers remain hash-stable.
 * Values must never refer back to a function or its owning NoiseChunk.
 */
public final class DensityFunctionHashMemo {
    private static final Cache<DensityFunction, Integer> HASHES = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(65_536)
            .build();

    private static final Hash.Strategy<DensityFunction> STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(DensityFunction function) {
            if (function == null) return 0;
            Integer cached = HASHES.getIfPresent(function);
            if (cached != null) return cached;

            // Compute outside cache locks. Concurrent misses may harmlessly compute the same stable hash.
            int hash = function.hashCode();
            HASHES.put(function, hash);
            return hash;
        }

        @Override
        public boolean equals(DensityFunction first, DensityFunction second) {
            return first == second || (first != null && first.equals(second));
        }
    };

    private DensityFunctionHashMemo() { }

    public static Map<DensityFunction, DensityFunction> newWrappingMap() {
        return new Object2ObjectOpenCustomHashMap<>(STRATEGY);
    }
}
