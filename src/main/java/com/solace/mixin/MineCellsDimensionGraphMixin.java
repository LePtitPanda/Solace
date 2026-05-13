package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import com.github.mim1q.minecells.dimension.MineCellsDimensionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MineCellsDimensionGraph.class, remap = false)
public abstract class MineCellsDimensionGraphMixin {
    @Redirect(
            method = "lambda$saveStuckPlayer$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;m_129880_(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;",
                    remap = false
            ),
            remap = false
    )
    @Coerce
    private static Object solace$rescueStuckPlayersToUndergarden(@Coerce Object server, @Coerce Object key) {
        return MineCellsDoorwayCompat.worldForKey(server, key);
    }
}
