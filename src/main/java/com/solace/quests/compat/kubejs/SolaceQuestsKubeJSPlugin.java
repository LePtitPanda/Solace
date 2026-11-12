package com.solace.quests.compat.kubejs;

import com.solace.quests.api.QuestApi;
import com.solace.quests.api.QuestEvent;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.player.PlayerEventJS;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

/**
 * KubeJS side of Solace Quests. Listed in kubejs.plugins.txt, which only KubeJS reads, so none of
 * this is touched when KubeJS is absent.
 *
 * <pre>
 * SolaceQuestEvents.questCompleted(event => { event.player.tell(event.id) })
 * SolaceQuests.registerCondition('is_sneaking', (player, data) => player.isCrouching())
 * SolaceQuests.progress(player, 'astralis:village/first_steps', 'my_objective', 1)
 * </pre>
 */
public class SolaceQuestsKubeJSPlugin extends KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("SolaceQuestEvents");
    public static final EventHandler QUEST_STARTED = GROUP.server("questStarted", () -> QuestEventJS.class);
    public static final EventHandler QUEST_COMPLETED = GROUP.server("questCompleted", () -> QuestEventJS.class);
    public static final EventHandler QUEST_FAILED = GROUP.server("questFailed", () -> QuestEventJS.class);
    public static final EventHandler PHASE_CHANGED = GROUP.server("phaseChanged", () -> QuestEventJS.class);
    public static final EventHandler OBJECTIVE_PROGRESSED = GROUP.server("objectiveProgressed", () -> QuestEventJS.class);
    public static final EventHandler DIALOGUE_CHOICE = GROUP.server("dialogueChoice", () -> QuestEventJS.class);
    public static final EventHandler REPUTATION_CHANGED = GROUP.server("reputationChanged", () -> QuestEventJS.class);

    /** {@code id}: quest, dialogue or faction. {@code detail}: phase, objective, node or rank. */
    public static class QuestEventJS extends PlayerEventJS {
        private final QuestEvent event;

        public QuestEventJS(QuestEvent event) {
            this.event = event;
        }

        @Override
        public Player getEntity() {
            return event.player;
        }

        public String getId() {
            return event.id.toString();
        }

        public String getDetail() {
            return event.detail;
        }

        public int getValue() {
            return event.value;
        }
    }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.addListener((QuestEvent event) -> {
            EventHandler handler = switch (event.type) {
                case QUEST_STARTED -> QUEST_STARTED;
                case QUEST_COMPLETED -> QUEST_COMPLETED;
                case QUEST_FAILED -> QUEST_FAILED;
                case PHASE_CHANGED -> PHASE_CHANGED;
                case OBJECTIVE_PROGRESSED -> OBJECTIVE_PROGRESSED;
                case DIALOGUE_CHOICE -> DIALOGUE_CHOICE;
                case REPUTATION_CHANGED -> REPUTATION_CHANGED;
            };
            if (handler.hasListeners()) {
                handler.post(ScriptType.SERVER, new QuestEventJS(event));
            }
        });
    }

    @Override
    public void registerEvents() {
        GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("SolaceQuests", QuestApi.class);
    }
}
