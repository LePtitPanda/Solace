package com.solace.quests.content;

import com.solace.quests.condition.Condition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * {@code icon} fields are "item:<id>" or "texture:<id>", or null. {@code hiddenDeps} and
 * {@code hiddenObjectives}: how many must be done before the player sees the quest (0 = no rule,
 * Integer.MAX_VALUE = all).
 */
public record Quest(ResourceLocation id, ResourceLocation chapter, Component title, Component description,
                    String icon, int x, int y, int requiresAtLeast, List<ResourceLocation> requires,
                    boolean optional, boolean hidden, int hiddenDeps, int hiddenObjectives,
                    boolean autoStart, boolean repeatable, int cooldownSeconds,
                    boolean autoClaim, int timeLimitSeconds, boolean retry, boolean abandonable,
                    Condition visibleWhen, List<Marker> markers, List<Phase> phases, List<Reward> rewards) {
    public static final String COMPLETE = "@complete";
    public static final String FAIL = "@fail";
    /** Objective types that complete by themselves when their (generated) condition holds. */
    public static final Set<String> AUTO = Set.of("solace:condition", "solace:reputation", "solace:faction_rank",
            "solace:advancement", "solace:enter_dimension", "solace:enter_biome", "solace:find_structure");

    public Phase phase(String id) {
        for (Phase phase : phases) {
            if (phase.id.equals(id)) {
                return phase;
            }
        }
        return null;
    }

    public record Phase(String id, Component story, List<Objective> objectives, List<Transition> transitions,
                        List<Reward> onEnter, List<Marker> markers) {
        public Objective objective(String id) {
            for (Objective objective : objectives) {
                if (objective.id.equals(id)) {
                    return objective;
                }
            }
            return null;
        }
    }

    /** {@code to} is a phase id, {@link #COMPLETE} or {@link #FAIL}. */
    public record Transition(String to, Condition when, String label, List<Reward> actions) {
    }

    /**
     * Flat on purpose: one record for every objective type, unused fields stay null/0.
     * {@code ref} is the dimension, dialogue or NPC binding; {@code name} the entity name or dialogue node.
     */
    public record Objective(String id, String type, Component text, String icon, int count, boolean optional,
                            boolean hidden, Target target, String name, boolean consume, ResourceLocation ref,
                            int x, int y, int z, int radius, Condition when) {
    }

    /**
     * A reward or an action: both are "effects" and share every type. Flat like {@link Objective}.
     * {@code name} is the command, flag, variable, phase, objective or custom handler name.
     */
    public record Reward(String id, String type, Component text, String icon, ResourceLocation target, int count,
                         CompoundTag nbt, String name, boolean on, String data) {
    }
}
