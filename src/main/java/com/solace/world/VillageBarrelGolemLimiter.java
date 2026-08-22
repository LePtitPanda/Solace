package com.solace.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

public final class VillageBarrelGolemLimiter {
    private static final ResourceLocation BARREL_GOLEM_ID =
            ResourceLocation.fromNamespaceAndPath("golemoverhaul", "barrel_golem");
    private static final int MAX_BARREL_GOLEMS = 5;
    private static final double HORIZONTAL_COUNT_RADIUS = 192.0D;
    private static final double VERTICAL_COUNT_RADIUS = 128.0D;

    private VillageBarrelGolemLimiter() {
    }

    public static boolean shouldRejectJoin(ServerLevel level, Entity joiningEntity) {
        if (!BARREL_GOLEM_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(joiningEntity.getType()))) {
            return false;
        }

        BlockPos pos = joiningEntity.blockPosition();
        StructureStart village = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        if (!village.isValid() && !level.isVillage(pos)) {
            return false;
        }

        AABB villageArea = new AABB(pos).inflate(
                HORIZONTAL_COUNT_RADIUS,
                VERTICAL_COUNT_RADIUS,
                HORIZONTAL_COUNT_RADIUS
        );
        int existingGolems = level.getEntities(joiningEntity, villageArea, entity ->
                entity.isAlive()
                        && BARREL_GOLEM_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()))
        ).size();
        return existingGolems >= MAX_BARREL_GOLEMS;
    }
}
