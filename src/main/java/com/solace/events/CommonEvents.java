package com.solace.events;

import com.solace.world.BalloonIntroManager;
import com.solace.world.SolaceIntroBalloonAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BalloonIntroManager.precomputeIntroVillage(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof SolaceIntroBalloonAccess access) || !access.solace$isIntroBalloon() || access.solace$hasLanded()) {
            return;
        }

        if (hasSolidBlockUnder(vehicle)) {
            access.solace$markLanded();
        }
    }

    private static boolean hasSolidBlockUnder(Entity entity) {
        BlockPos base = BlockPos.containing(entity.getX(), entity.getBoundingBox().minY - 0.12D, entity.getZ());

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = base.offset(dx, 0, dz);
                BlockState state = entity.level().getBlockState(pos);
                if (!state.isAir() && state.getCollisionShape(entity.level(), pos).max(Direction.Axis.Y) > 0.0D) {
                    return true;
                }
            }
        }

        return false;
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (!event.isDismounting()) {
            return;
        }

        Entity mounted = event.getEntityBeingMounted();
        if (mounted instanceof SolaceIntroBalloonAccess access && access.solace$isIntroBalloon() && !access.solace$hasLanded()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BalloonIntroManager.tryStartIntro(player);
        }
    }
}
