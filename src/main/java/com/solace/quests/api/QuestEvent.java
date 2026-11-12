package com.solace.quests.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus (server thread) when something happens in Solace Quests.
 * {@code id} is the quest, dialogue or faction; {@code detail} the phase, objective, node or rank;
 * {@code value} the progress, choice index or new reputation.
 */
public class QuestEvent extends Event {
    public enum Type { QUEST_STARTED, QUEST_COMPLETED, QUEST_FAILED, PHASE_CHANGED, OBJECTIVE_PROGRESSED,
        DIALOGUE_CHOICE, REPUTATION_CHANGED }

    public final Type type;
    public final ServerPlayer player;
    public final ResourceLocation id;
    public final String detail;
    public final int value;

    public QuestEvent(Type type, ServerPlayer player, ResourceLocation id, String detail, int value) {
        this.type = type;
        this.player = player;
        this.id = id;
        this.detail = detail;
        this.value = value;
    }
}
