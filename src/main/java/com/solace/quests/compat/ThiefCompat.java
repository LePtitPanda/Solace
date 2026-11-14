package com.solace.quests.compat;

import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.QuestLogic;
import com.solace.quests.state.ServerCtx;
import io.github.mortuusars.thief.forge.api.event.CrimeCommitedEvent;
import io.github.mortuusars.thief.forge.api.event.GiftGivenEvent;
import io.github.mortuusars.thief.forge.api.event.ReputationLevelChangedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * Thief builds on vanilla villager gossip, which already moves Solace's "villager gossip" factions.
 * These hooks only re-check objectives right away. Only loaded when the thief mod is present.
 */
public final class ThiefCompat {
    private ThiefCompat() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((ReputationLevelChangedEvent event) -> refresh(event.criminal));
        MinecraftForge.EVENT_BUS.addListener((CrimeCommitedEvent event) -> refresh(event.criminal));
        MinecraftForge.EVENT_BUS.addListener((GiftGivenEvent event) -> refresh(event.player));
    }

    private static void refresh(Object who) {
        if (who instanceof ServerPlayer player) {
            ServerCtx ctx = QuestEngine.ctx(player);
            QuestLogic.evaluate(ctx, QuestEngine.events(player));
        }
    }
}
