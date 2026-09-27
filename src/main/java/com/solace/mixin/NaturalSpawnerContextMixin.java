package com.solace.mixin;

import com.solace.spawn.OverworldSpawnManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerContextMixin {
    @ModifyVariable(method = "spawnCategoryForChunk", at = @At("STORE"), ordinal = 0)
    private static BlockPos solace$sampleCreatureGround(
            BlockPos original, MobCategory category, ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnPredicate predicate, NaturalSpawner.AfterSpawnCallback afterSpawn
    ) {
        if (category != MobCategory.CREATURE) return original;
        BlockPos surface = OverworldSpawnManager.creatureSurfaceCandidate(level, original);
        return surface == null ? original : surface;
    }

    @Inject(method = "spawnForChunk", at = @At("HEAD"))
    private static void solace$beginOverworldNativeSpawn(
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnState spawnState,
            boolean spawnFriendlies,
            boolean spawnEnemies,
            boolean spawnPersistent,
            CallbackInfo callbackInfo
    ) {
        OverworldSpawnManager.beginNativeSpawn(level);
    }

    @Inject(method = "spawnForChunk", at = @At("RETURN"))
    private static void solace$endOverworldNativeSpawn(
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnState spawnState,
            boolean spawnFriendlies,
            boolean spawnEnemies,
            boolean spawnPersistent,
            CallbackInfo callbackInfo
    ) {
        OverworldSpawnManager.endNativeSpawn();
    }
}
