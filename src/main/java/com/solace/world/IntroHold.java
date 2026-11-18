package com.solace.world;

import com.solace.quests.api.QuestApi;
import com.solace.quests.network.QuestNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a new player's client behind a loading screen (sounds muted) from his first join until he has been in
 * the dream for a moment, so the first thing he sees is the dream. The client also waits for the ground under
 * him to be drawn before it lifts the screen.
 */
public final class IntroHold {
    private static final int TICKS_IN_DREAM = 40;
    private static final int FAILSAFE_TICKS = 20 * 60;
    // {ticks since the client said it was ready, ticks in dream, ready 0/1}. The failsafe only counts after ready:
    // a minimized or background game draws nothing, so it can take any time to get there.
    private static final Map<UUID, int[]> HELD = new HashMap<>();

    private IntroHold() {
    }

    public static void hold(ServerPlayer player) {
        HELD.put(player.getUUID(), new int[3]);
        QuestNetwork.introHold(player, true);
    }

    /**
     * The held client has drawn the ground around it: it is past login and will see what happens next. Sets the
     * flag {@code intro.ready}; the opening quest waits for it before sending the player into the dream, because a
     * teleport during login is undone by login-protection mods.
     */
    public static void ready(ServerPlayer player) {
        com.mojang.logging.LogUtils.getLogger().info("[Solace] Intro ready from {} at {} in {}.", player.getGameProfile().getName(),
                player.blockPosition().toShortString(), player.level().dimension().location());
        QuestApi.setFlag(player, "intro.ready", true);
        int[] ticks = HELD.get(player.getUUID());
        if (ticks != null) ticks[2] = 1;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        int[] ticks = HELD.get(player.getUUID());
        if (ticks == null) return;
        if (ticks[2] == 1) ticks[0]++;
        ticks[1] = DreamDimension.isDream(player.level()) ? ticks[1] + 1 : 0;
        if (ticks[1] >= TICKS_IN_DREAM || ticks[0] >= FAILSAFE_TICKS) {
            HELD.remove(player.getUUID());
            QuestNetwork.introHold(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
    }
}
