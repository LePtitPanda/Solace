package com.solace.quests.state;

import com.solace.quests.condition.Ctx;
import com.solace.quests.content.Chapter;
import com.solace.quests.content.Quest;
import com.solace.quests.content.Quest.Objective;
import com.solace.quests.content.Quest.Phase;
import com.solace.quests.content.Quest.Reward;
import com.solace.quests.content.Quest.Transition;
import com.solace.quests.state.PlayerQuestData.QuestState;
import com.solace.quests.state.PlayerQuestData.Status;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The quest state machine. Pure: it only touches {@link Ctx#data()} and reports what happened
 * through {@link Events}, so it runs in unit tests without a server.
 */
public final class QuestLogic {
    public enum View { LOCKED, AVAILABLE, ACTIVE, CLAIMABLE, COMPLETED, FAILED }

    public interface Events {
        default void started(Quest quest) {
        }

        default void phase(Quest quest, Phase phase) {
        }

        default void completed(Quest quest) {
        }

        default void failed(Quest quest) {
        }

        /** Actions of a transition or of a phase's on_enter, to run in order. */
        default void actions(Quest quest, List<Reward> actions) {
        }
    }

    public static final int MAX_TRACKED = 5;
    private static final int MAX_CHAIN = 32;
    private static int viewDepth;

    private QuestLogic() {
    }

    /** What the quest looks like to this owner right now. */
    public static View view(Ctx ctx, Quest quest) {
        // A chapter unlock condition may ask about a quest of that same chapter; stop the loop.
        if (viewDepth > 8) {
            return View.LOCKED;
        }
        viewDepth++;
        try {
            PlayerQuestData data = ctx.data();
            QuestState state = data.quests.get(quest.id());
            if (state != null) {
                if (state.status == Status.ACTIVE) {
                    return View.ACTIVE;
                }
                if (state.status == Status.COMPLETED && quest.rewards().stream()
                        .anyMatch(reward -> !state.claimed.contains(reward.id()))) {
                    return View.CLAIMABLE;
                }
                boolean again = quest.repeatable() || state.status == Status.FAILED && quest.retry();
                if (!again || ctx.gameTime() < state.cooldownUntil) {
                    return state.status == Status.FAILED ? View.FAILED : View.COMPLETED;
                }
            }
            if (data.unlocked.contains(quest.id())) {
                return View.AVAILABLE;
            }
            if (!chapterOpen(ctx, quest.chapter())) {
                return View.LOCKED;
            }
            int done = 0;
            for (ResourceLocation required : quest.requires()) {
                QuestState other = data.quests.get(required);
                if (other != null && other.completions > 0) {
                    done++;
                }
            }
            return done >= quest.requiresAtLeast() ? View.AVAILABLE : View.LOCKED;
        } finally {
            viewDepth--;
        }
    }

    public static boolean chapterOpen(Ctx ctx, ResourceLocation chapterId) {
        Chapter chapter = ctx.content().chapters.get(chapterId);
        if (chapter == null || chapter.unlock() == null || ctx.data().unlocked.contains(chapterId)) {
            return true;
        }
        ResourceLocation outer = ctx.quest;
        ctx.quest = null;
        try {
            return chapter.unlock().test(ctx);
        } finally {
            ctx.quest = outer;
        }
    }

    /** "completed" means completed at least once, so it stays true for claimable and repeatable quests. */
    public static boolean is(Ctx ctx, ResourceLocation questId, View view) {
        Quest quest = ctx.content().quests.get(questId);
        if (quest == null) {
            return false;
        }
        if (view == View.COMPLETED) {
            QuestState state = ctx.data().quests.get(questId);
            return state != null && state.completions > 0;
        }
        return view(ctx, quest) == view;
    }

    /** True when the listed objectives (default: every non-optional one) of the context quest's phase are done. */
    public static boolean objectivesComplete(Ctx ctx, List<String> objectives) {
        Quest quest = ctx.quest == null ? null : ctx.content().quests.get(ctx.quest);
        QuestState state = ctx.quest == null ? null : ctx.data().quests.get(ctx.quest);
        Phase phase = quest == null || state == null ? null : quest.phase(state.phase);
        if (phase == null) {
            return false;
        }
        for (Objective objective : phase.objectives()) {
            boolean wanted = objectives == null ? !objective.optional() : objectives.contains(objective.id());
            if (wanted && state.progress.getOrDefault(objective.id(), 0) < objective.count()) {
                return false;
            }
        }
        return true;
    }

    /** Starts the quest if it is available (or {@code force}). Resets progress of a repeated quest. */
    public static boolean start(Ctx ctx, Quest quest, boolean force, Events events) {
        if (!force && view(ctx, quest) != View.AVAILABLE) {
            return false;
        }
        begin(ctx, quest, "start", events);
        evaluate(ctx, events);
        return true;
    }

    private static void begin(Ctx ctx, Quest quest, String via, Events events) {
        PlayerQuestData data = ctx.data();
        QuestState state = data.quests.computeIfAbsent(quest.id(), id -> new QuestState());
        Phase first = quest.phases().get(0);
        state.status = Status.ACTIVE;
        state.phase = first.id();
        state.progress.clear();
        state.claimed.clear();
        state.startedAt = ctx.gameTime();
        state.log(ctx.gameTime(), state.phase, via);
        if (data.tracked.size() < MAX_TRACKED && !data.tracked.contains(quest.id())) {
            data.tracked.add(quest.id());
        }
        ctx.changed();
        events.started(quest);
        if (!first.onEnter().isEmpty()) {
            events.actions(quest, first.onEnter());
        }
    }

    /** Sets (not adds) an objective's progress in the current phase. Returns false if it does not apply. */
    public static boolean setProgress(Ctx ctx, Quest quest, String objectiveId, int value, Events events) {
        QuestState state = ctx.data().quests.get(quest.id());
        Phase phase = state == null || state.status != Status.ACTIVE ? null : quest.phase(state.phase);
        Objective objective = phase == null ? null : phase.objective(objectiveId);
        if (objective == null) {
            return false;
        }
        int clamped = Math.max(0, Math.min(objective.count(), value));
        if (state.progress.getOrDefault(objectiveId, 0) != clamped) {
            state.progress.put(objectiveId, clamped);
            ctx.changed();
            evaluate(ctx, events);
        }
        return true;
    }

    public static boolean addProgress(Ctx ctx, Quest quest, String objectiveId, int amount, Events events) {
        QuestState state = ctx.data().quests.get(quest.id());
        int now = state == null ? 0 : state.progress.getOrDefault(objectiveId, 0);
        return setProgress(ctx, quest, objectiveId, now + amount, events);
    }

    /** Moves an active quest to a phase, {@link Quest#COMPLETE} or {@link Quest#FAIL}. */
    public static boolean move(Ctx ctx, Quest quest, String to, String via, Events events) {
        QuestState state = ctx.data().quests.get(quest.id());
        if (state == null || state.status != Status.ACTIVE || !apply(ctx, quest, state, to, via, List.of(), events)) {
            return false;
        }
        evaluate(ctx, events);
        return true;
    }

    private static boolean apply(Ctx ctx, Quest quest, QuestState state, String to, String via,
                                 List<Reward> actions, Events events) {
        Phase phase = quest.phase(to);
        boolean end = to.equals(Quest.COMPLETE) || to.equals(Quest.FAIL);
        if (!end && phase == null) {
            return false;
        }
        state.log(ctx.gameTime(), to, via);
        if (end) {
            state.status = to.equals(Quest.COMPLETE) ? Status.COMPLETED : Status.FAILED;
            state.cooldownUntil = ctx.gameTime() + quest.cooldownSeconds() * 20L;
            ctx.data().tracked.remove(quest.id());
            if (state.status == Status.COMPLETED) {
                state.completions++;
                ctx.changed();
                events.completed(quest);
            } else {
                ctx.changed();
                events.failed(quest);
            }
        } else {
            state.phase = to;
            ctx.changed();
            events.phase(quest, phase);
        }
        if (!actions.isEmpty()) {
            events.actions(quest, actions);
        }
        if (phase != null && !phase.onEnter().isEmpty()) {
            events.actions(quest, phase.onEnter());
        }
        return true;
    }

    /**
     * Re-checks auto-start quests, time limits, self-completing objectives and transitions of every
     * active quest until nothing moves. Bounded, so content that loops cannot hang the server.
     */
    public static void evaluate(Ctx ctx, Events events) {
        ResourceLocation outer = ctx.quest;
        try {
            for (int round = 0; round < MAX_CHAIN; round++) {
                boolean moved = false;
                for (Quest quest : ctx.content().autoStart) {
                    if (view(ctx, quest) == View.AVAILABLE) {
                        begin(ctx, quest, "auto_start", events);
                        moved = true;
                    }
                }
                for (Map.Entry<ResourceLocation, QuestState> entry : List.copyOf(ctx.data().quests.entrySet())) {
                    QuestState state = entry.getValue();
                    Quest quest = ctx.content().quests.get(entry.getKey());
                    if (quest == null || state.status != Status.ACTIVE) {
                        continue;
                    }
                    if (quest.timeLimitSeconds() > 0
                            && ctx.gameTime() >= state.startedAt + quest.timeLimitSeconds() * 20L) {
                        moved |= apply(ctx, quest, state, Quest.FAIL, "time_limit", List.of(), events);
                        continue;
                    }
                    Phase phase = quest.phase(state.phase);
                    if (phase == null) {
                        // The phase id was removed from the content: restart from the first phase.
                        phase = quest.phases().get(0);
                        state.phase = phase.id();
                        state.log(ctx.gameTime(), phase.id(), "repaired");
                        ctx.changed();
                    }
                    ctx.quest = quest.id();
                    for (Objective objective : phase.objectives()) {
                        if (Quest.AUTO.contains(objective.type()) && objective.when() != null
                                && state.progress.getOrDefault(objective.id(), 0) < 1 && objective.when().test(ctx)) {
                            state.progress.put(objective.id(), 1);
                            ctx.changed();
                        }
                    }
                    moved |= step(ctx, quest, state, phase, events);
                }
                if (!moved) {
                    return;
                }
            }
        } finally {
            ctx.quest = outer;
        }
    }

    private static boolean step(Ctx ctx, Quest quest, QuestState state, Phase phase, Events events) {
        if (phase.transitions().isEmpty()) {
            // No explicit transitions: finishing the objectives moves to the next phase in file order.
            if (!objectivesComplete(ctx, null)) {
                return false;
            }
            int next = quest.phases().indexOf(phase) + 1;
            String to = next < quest.phases().size() ? quest.phases().get(next).id() : Quest.COMPLETE;
            return apply(ctx, quest, state, to, "objectives", List.of(), events);
        }
        for (Transition transition : phase.transitions()) {
            if (transition.when().test(ctx)) {
                return apply(ctx, quest, state, transition.to(),
                        transition.label() == null ? "transition" : transition.label(), transition.actions(), events);
            }
        }
        return false;
    }

    /**
     * Marks rewards claimed and returns the ones to hand out. Marking happens first, so a reward
     * that fails while being granted can never be claimed twice.
     */
    public static List<Reward> claim(Ctx ctx, Quest quest, String rewardId) {
        List<Reward> granted = new ArrayList<>();
        QuestState state = ctx.data().quests.get(quest.id());
        if (state == null || state.status != Status.COMPLETED) {
            return granted;
        }
        for (Reward reward : quest.rewards()) {
            if ((rewardId == null || rewardId.equals(reward.id())) && state.claimed.add(reward.id())) {
                granted.add(reward);
            }
        }
        if (!granted.isEmpty()) {
            ctx.changed();
        }
        return granted;
    }

    /** Removes all state of a quest, as if it had never been started. */
    public static boolean reset(Ctx ctx, ResourceLocation questId) {
        if (ctx.data().quests.remove(questId) == null) {
            return false;
        }
        ctx.data().tracked.remove(questId);
        ctx.changed();
        return true;
    }

    /** The player gives up an active quest; it becomes available again. */
    public static boolean abandon(Ctx ctx, Quest quest) {
        QuestState state = ctx.data().quests.get(quest.id());
        if (state == null || state.status != Status.ACTIVE || !quest.abandonable()) {
            return false;
        }
        int completions = state.completions;
        reset(ctx, quest.id());
        if (completions > 0) {
            // Keep the fact that it was completed before (repeatable quests), drop the run.
            QuestState kept = new QuestState();
            kept.status = Status.COMPLETED;
            kept.completions = completions;
            quest.rewards().forEach(reward -> kept.claimed.add(reward.id()));
            ctx.data().quests.put(quest.id(), kept);
        }
        return true;
    }

    public static boolean track(Ctx ctx, ResourceLocation questId, boolean on) {
        List<ResourceLocation> tracked = ctx.data().tracked;
        boolean changed = on ? !tracked.contains(questId) && tracked.size() < MAX_TRACKED && tracked.add(questId)
                : tracked.remove(questId);
        if (changed) {
            ctx.changed();
        }
        return changed;
    }
}
