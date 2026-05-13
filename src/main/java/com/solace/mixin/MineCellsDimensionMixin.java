package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import com.github.mim1q.minecells.dimension.MineCellsDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MineCellsDimension.class, remap = false)
public abstract class MineCellsDimensionMixin {
    @Redirect(
            method = "getWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;m_129880_(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;",
                    remap = false
            ),
            remap = false
    )
    @Coerce
    private Object solace$routeOverworldKeyToUndergarden(@Coerce Object server, @Coerce Object key) {
        return MineCellsDoorwayCompat.worldForKey(server, key);
    }
}
