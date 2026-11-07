package com.solace.mixin;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.createmod.catnip.render.ShadeSeparatingSuperByteBuffer", remap = false)
public abstract class CatnipLightCacheMixin {
    @Shadow(remap = false) private static Long2IntMap WORLD_LIGHT_CACHE;

    /**
     * @author Solace
     * @reason Allocate the world position only on cache misses, preserving the original cache and its lifetime.
     */
    @Overwrite(remap = false)
    private static int getLight(BlockAndTintGetter world, Vector4f position) {
        int x = Mth.floor((double) position.x());
        int y = Mth.floor((double) position.y());
        int z = Mth.floor((double) position.z());
        long key = BlockPos.asLong(x, y, z);
        int light = WORLD_LIGHT_CACHE.getOrDefault(key, Integer.MIN_VALUE);
        // Also preserve a cached sentinel value if a lighting extension supplies one.
        if (light != Integer.MIN_VALUE || WORLD_LIGHT_CACHE.containsKey(key)) return light;
        light = LevelRenderer.getLightColor(world, new BlockPos(x, y, z));
        WORLD_LIGHT_CACHE.put(key, light);
        return light;
    }
}
