package com.solace.events;

import com.solace.world.BalloonIntroManager;
import com.solace.world.SolaceIntroBalloonAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
