package com.solace.quests;

import com.solace.quests.content.QuestContentLoader;
import com.solace.quests.state.QuestEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Solace Quests wiring: reload listener, commands and the game events that drive objectives. */
public final class SolaceQuests {
    private SolaceQuests() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(SolaceQuests.class);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestContentLoader());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
    }

    // LOWEST and not receiving cancelled events: only count what actually happened.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && real(player)) {
            LivingEntity victim = event.getEntity();
            QuestEngine.objective(player, "solace:kill", objective -> objective.target().matches(victim)
                    && (objective.name() == null || victim.getName().getString().contains(objective.name())), 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && real(player)) {
            QuestEngine.objective(player, "solace:break_block",
                    objective -> objective.target().matches(event.getState()), 1);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)) {
            QuestEngine.objective(player, "solace:place_block",
                    objective -> objective.target().matches(event.getPlacedBlock()), 1);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Each player is visited once a second, spread over the 20 ticks by entity id.
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if ((player.tickCount + player.getId()) % 20 == 0) {
                QuestEngine.tick(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestEngine.tick(player);
        }
    }

    private static boolean real(ServerPlayer player) {
        return !(player instanceof FakePlayer);
    }
}
