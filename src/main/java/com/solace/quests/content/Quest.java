package com.solace.quests.content;

import com.solace.quests.condition.Condition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record Quest(ResourceLocation id, ResourceLocation chapter, Component title, Component description,
                    int requiresAtLeast, List<ResourceLocation> requires, boolean optional, boolean hidden,
                    boolean autoStart, boolean repeatable, int cooldownSeconds, boolean autoClaim,
                    List<Phase> phases, List<Reward> rewards) {
    public static final String COMPLETE = "@complete";
    public static final String FAIL = "@fail";

    public Phase phase(String id) {
        for (Phase phase : phases) {
            if (phase.id.equals(id)) {
                return phase;
            }
        }
        return null;
    }

    public record Phase(String id, Component story, List<Objective> objectives, List<Transition> transitions) {
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
    public record Transition(String to, Condition when, String label) {
    }

    /** Flat on purpose: one record for every objective type, unused fields stay null/0. */
    public record Objective(String id, String type, Component text, int count, boolean optional, Target target,
                            String name, boolean consume, ResourceLocation dimension, int x, int y, int z,
                            int radius, Condition when) {
    }

    /** Flat on purpose, like {@link Objective}. {@code name} is the command, flag or variable name. */
    public record Reward(String id, String type, ResourceLocation target, int count, CompoundTag nbt, String name,
                         boolean on) {
    }
}
