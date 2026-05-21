package com.solace.events;

import com.mojang.brigadier.CommandDispatcher;
import com.solace.world.BalloonIntroManager;
import com.solace.world.SolaceIntroBalloonAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommonEvents {
    private static final Set<UUID> DEATH_DISMOUNT_BYPASS = ConcurrentHashMap.newKeySet();

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

        BalloonIntroManager.refreshIntroSafetySpawn(player, vehicle);
        boolean wasLanded = access.solace$hasLanded();
        access.solace$tickLandingMovementCheck();
        if (!wasLanded && access.solace$hasLanded()) {
            BalloonIntroManager.completeIntroLanding(player, vehicle);
        }
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (!event.isDismounting()) {
            return;
        }

        Entity mounted = event.getEntityBeingMounted();
        Entity rider = event.getEntityMounting();
        if (rider instanceof ServerPlayer player && DEATH_DISMOUNT_BYPASS.contains(player.getUUID())) {
            return;
        }

        if (mounted instanceof SolaceIntroBalloonAccess access && access.solace$isIntroBalloon() && !access.solace$hasLanded()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof SolaceIntroBalloonAccess access) || !access.solace$isIntroBalloon() || access.solace$hasLanded()) {
            return;
        }

        DEATH_DISMOUNT_BYPASS.add(player.getUUID());
        try {
            player.stopRiding();
        } finally {
            DEATH_DISMOUNT_BYPASS.remove(player.getUUID());
        }
        vehicle.discard();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BalloonIntroManager.tryStartIntro(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("solace")
                .then(Commands.literal("stop_balloon")
                        .executes(context -> stopIntroBalloon(context.getSource()))));
    }

    private static int stopIntroBalloon(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can stop an intro balloon."));
            return 0;
        }

        if (!player.isCreative()) {
            source.sendFailure(Component.literal("You must be in creative mode to stop the intro balloon."));
            return 0;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof SolaceIntroBalloonAccess access) || !access.solace$isIntroBalloon() || access.solace$hasLanded()) {
            source.sendFailure(Component.literal("You are not riding an active intro balloon."));
            return 0;
        }

        access.solace$markLanded();
        BalloonIntroManager.completeIntroLanding(player, vehicle);
        source.sendSuccess(() -> Component.literal("Stopped the intro balloon."), false);
        return 1;
    }
}
