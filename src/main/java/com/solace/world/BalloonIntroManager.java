package com.solace.world;

import com.solace.Solace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public final class BalloonIntroManager {
    public static final String INTRO_BALLOON_KEY = Solace.MOD_ID + ":breezy_intro_balloon";
    public static final String TARGET_X_KEY = Solace.MOD_ID + ":breezy_intro_target_x";
    public static final String TARGET_Y_KEY = Solace.MOD_ID + ":breezy_intro_target_y";
    public static final String TARGET_Z_KEY = Solace.MOD_ID + ":breezy_intro_target_z";
    public static final String START_SPAWN_X_KEY = Solace.MOD_ID + ":breezy_intro_start_spawn_x";
    public static final String START_SPAWN_Y_KEY = Solace.MOD_ID + ":breezy_intro_start_spawn_y";
    public static final String START_SPAWN_Z_KEY = Solace.MOD_ID + ":breezy_intro_start_spawn_z";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = Solace.MOD_ID + "_world_data";
    private static final ResourceLocation BREEZY_BALLOON_ID = new ResourceLocation("breezy", "hot_air_balloon");
    private static final ResourceKey<Structure> PLAINS_VILLAGE = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation("minecraft", "village_plains"));
    private static final int SEARCH_RADIUS_CHUNKS = 128;
    private static final double START_DISTANCE = 460.0D;
    private static final int START_HEIGHT_ABOVE_SEA_LEVEL = 150;
    private static final int ROUTE_PRELOAD_HALF_WIDTH_CHUNKS = 1;

    private BalloonIntroManager() {
    }

    public static void precomputeIntroVillage(ServerLevel level) {
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;

        SolaceWorldData data = getData(level);
        if (data.hasBreezyVillageIntroVillagePos()) {
            preloadIntroRouteIfNeeded(level, data, data.getBreezyVillageIntroVillagePos());
            return;
        }

        LOGGER.info("[Solace] Finding Breezy intro village. This may take a few seconds during world load.");
        BlockPos villagePos = findPlainsVillage(level);
        if (villagePos == null) {
            LOGGER.warn("[Solace] Could not find minecraft:village_plains within {} chunks.", SEARCH_RADIUS_CHUNKS);
            return;
        }

        LOGGER.info("[Solace] Cached Breezy intro village at {} {} {}.", villagePos.getX(), villagePos.getY(), villagePos.getZ());
        data.setBreezyVillageIntroVillagePos(villagePos);
        preloadIntroRouteIfNeeded(level, data, villagePos);
    }

    public static void tryStartIntro(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;

        SolaceWorldData data = getData(level);
        if (data.hasPlayerCompletedBreezyVillageIntro(player.getUUID())) return;

        if (!ForgeRegistries.ENTITY_TYPES.containsKey(BREEZY_BALLOON_ID)) {
            LOGGER.warn("[Solace] Breezy balloon entity is not registered; skipping intro.");
            return;
        }

        BlockPos villagePos = data.getBreezyVillageIntroVillagePos();
        if (villagePos == null) {
            villagePos = findPlainsVillage(level);
            if (villagePos != null) {
                data.setBreezyVillageIntroVillagePos(villagePos);
            }
        }

        if (villagePos == null) {
            LOGGER.warn("[Solace] Could not find minecraft:village_plains within {} chunks.", SEARCH_RADIUS_CHUNKS);
            return;
        }

        Vec3 target = Vec3.atCenterOf(villagePos);
        Vec3 start = computeStartPosition(level, target);
        BlockPos startSpawn = getGroundSpawnPos(level, start);
        Entity balloon = spawnIntroBalloon(level, start, target, startSpawn);
        if (balloon == null) return;

        setRespawnPosition(player, level.dimension(), startSpawn, player.getYRot());
        player.teleportTo(level, start.x, start.y + 0.7D, start.z, player.getYRot(), player.getXRot());
        player.startRiding(balloon, true);
        data.setPlayerCompletedBreezyVillageIntro(player.getUUID());
    }

    public static void refreshIntroSafetySpawn(ServerPlayer player, Entity balloon) {
        if (!player.serverLevel().dimension().equals(ServerLevel.OVERWORLD)) return;
        if (!balloon.getPersistentData().getBoolean(INTRO_BALLOON_KEY)) return;

        setRespawnPosition(player, ServerLevel.OVERWORLD, getStoredStartSpawnPos(balloon), player.getYRot());
    }

    public static void completeIntroLanding(ServerPlayer player, Entity balloon) {
        if (!player.serverLevel().dimension().equals(ServerLevel.OVERWORLD)) return;
        if (!balloon.getPersistentData().getBoolean(INTRO_BALLOON_KEY)) return;

        BlockPos landingPos = getGroundSpawnPos(player.serverLevel(), player.position());
        setRespawnPosition(player, ServerLevel.OVERWORLD, landingPos, player.getYRot());
        getData(player.serverLevel()).setPlayerCompletedBreezyVillageIntro(player.getUUID());
    }

    private static SolaceWorldData getData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SolaceWorldData::load, SolaceWorldData::new, DATA_NAME);
    }

    private static BlockPos findPlainsVillage(ServerLevel level) {
        return level.findNearestMapStructure(
                net.minecraft.tags.StructureTags.VILLAGE,
                level.getSharedSpawnPos(),
                SEARCH_RADIUS_CHUNKS,
                false
        );
    }

    private static Vec3 computeStartPosition(ServerLevel level, Vec3 villageCenter) {
        long seed = level.getSeed() ^ 0x51A7CEBEEFL;
        double angle = ((seed & 0xFFFFL) / (double) 0x10000L) * Mth.TWO_PI;
        Vec3 travelDirection = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)).normalize();
        Vec3 horizontalStart = villageCenter.subtract(travelDirection.scale(START_DISTANCE));
        int x = Mth.floor(horizontalStart.x);
        int z = Mth.floor(horizontalStart.z);
        double y = Mth.clamp(level.getSeaLevel() + START_HEIGHT_ABOVE_SEA_LEVEL, level.getMinBuildHeight() + 32, level.getMaxBuildHeight() - 32);
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    private static void preloadIntroRouteIfNeeded(ServerLevel level, SolaceWorldData data, BlockPos villagePos) {
        if (data.isBreezyVillageIntroRoutePreloaded()) return;

        Vec3 target = Vec3.atCenterOf(villagePos);
        Vec3 start = computeStartPosition(level, target);
        int loadedChunks = preloadChunkCorridor(level, start, target);
        data.setBreezyVillageIntroRoutePreloaded(true);
        LOGGER.info("[Solace] Preloaded {} chunks along Breezy intro route.", loadedChunks);
    }

    private static int preloadChunkCorridor(ServerLevel level, Vec3 start, Vec3 target) {
        int startChunkX = Mth.floor(start.x) >> 4;
        int startChunkZ = Mth.floor(start.z) >> 4;
        int targetChunkX = Mth.floor(target.x) >> 4;
        int targetChunkZ = Mth.floor(target.z) >> 4;
        int dx = targetChunkX - startChunkX;
        int dz = targetChunkZ - startChunkZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int loadedChunks = 0;
        java.util.HashSet<Long> seenChunks = new java.util.HashSet<>();

        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0.0D : step / (double) steps;
            int centerChunkX = Mth.floor(Mth.lerp(progress, startChunkX, targetChunkX));
            int centerChunkZ = Mth.floor(Mth.lerp(progress, startChunkZ, targetChunkZ));

            for (int offsetX = -ROUTE_PRELOAD_HALF_WIDTH_CHUNKS; offsetX <= ROUTE_PRELOAD_HALF_WIDTH_CHUNKS; offsetX++) {
                for (int offsetZ = -ROUTE_PRELOAD_HALF_WIDTH_CHUNKS; offsetZ <= ROUTE_PRELOAD_HALF_WIDTH_CHUNKS; offsetZ++) {
                    int chunkX = centerChunkX + offsetX;
                    int chunkZ = centerChunkZ + offsetZ;
                    long chunkKey = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
                    if (seenChunks.add(chunkKey)) {
                        level.getChunk(chunkX, chunkZ);
                        loadedChunks++;
                    }
                }
            }
        }

        return loadedChunks;
    }

    private static void setRespawnPosition(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, float angle) {
        player.setRespawnPosition(dimension, pos, angle, true, false);
    }

    private static BlockPos getStoredStartSpawnPos(Entity balloon) {
        return new BlockPos(
                balloon.getPersistentData().getInt(START_SPAWN_X_KEY),
                balloon.getPersistentData().getInt(START_SPAWN_Y_KEY),
                balloon.getPersistentData().getInt(START_SPAWN_Z_KEY)
        );
    }

    private static BlockPos getGroundSpawnPos(ServerLevel level, Vec3 position) {
        int x = Mth.floor(position.x);
        int z = Mth.floor(position.z);
        level.getChunk(x >> 4, z >> 4);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    private static Entity spawnIntroBalloon(ServerLevel level, Vec3 start, Vec3 target, BlockPos startSpawn) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(BREEZY_BALLOON_ID);
        if (type == null) return null;
        Entity entity = type.create(level);
        if (entity == null) return null;
        entity.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        entity.getPersistentData().putBoolean(INTRO_BALLOON_KEY, true);
        entity.getPersistentData().putDouble(TARGET_X_KEY, target.x);
        entity.getPersistentData().putDouble(TARGET_Y_KEY, target.y);
        entity.getPersistentData().putDouble(TARGET_Z_KEY, target.z);
        entity.getPersistentData().putInt(START_SPAWN_X_KEY, startSpawn.getX());
        entity.getPersistentData().putInt(START_SPAWN_Y_KEY, startSpawn.getY());
        entity.getPersistentData().putInt(START_SPAWN_Z_KEY, startSpawn.getZ());
        if (entity instanceof SolaceIntroBalloonAccess access) {
            access.solace$setIntroTarget(target.x, target.y, target.z);
        }
        callIntSetter(entity, "setLitness", 3);
        callIntSetter(entity, "setSandbags", 0);
        level.addFreshEntity(entity);
        return entity;
    }

    private static void callIntSetter(Entity entity, String methodName, int value) {
        try {
            Method method = entity.getClass().getMethod(methodName, int.class);
            method.invoke(entity, value);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[Solace] Could not call Breezy balloon method {}", methodName, e);
        }
    }
}
