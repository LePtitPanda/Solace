package com.solace.quests.content;

import com.google.gson.JsonElement;
import com.solace.quests.condition.Condition;
import com.solace.quests.condition.Conditions;
import com.solace.quests.content.Quest.Objective;
import com.solace.quests.content.Quest.Phase;
import com.solace.quests.content.Quest.Reward;
import com.solace.quests.content.Quest.Transition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** JSON to definitions. Every problem is reported through {@link In}; nothing throws. */
public final class QuestParser {
    private QuestParser() {
    }

    public static Chapter chapter(ResourceLocation id, In in) {
        in.skip("icon");
        if (in.has("id")) {
            in.warn("id", "ignored: the id comes from the file location");
            in.skip("id");
        }
        Chapter chapter = new Chapter(id, in.text("title", true), in.text("description", false),
                in.integer("order", 0, Integer.MIN_VALUE), condition(in, "unlock"),
                in.bool("hidden_until_unlocked", false));
        in.done();
        return chapter;
    }

    public static Quest quest(ResourceLocation id, In in) {
        // Read by later phases (book layout, markers, time limits); valid today, just unused.
        in.skip("icon", "position", "markers", "time_limit_seconds", "on_fail", "visible_when", "abandonable");
        if (in.has("id")) {
            in.warn("id", "ignored: the id comes from the file location");
            in.skip("id");
        }
        ResourceLocation chapter = in.ref("chapter", "chapters", true);
        Component title = in.text("title", true);
        Component description = in.text("description", false);

        List<ResourceLocation> requires = new ArrayList<>();
        int atLeast = 0;
        In requiresIn = in.obj("requires");
        if (requiresIn != null) {
            List<String> ids = requiresIn.strs("quests");
            for (String text : ids) {
                ResourceLocation required = ResourceLocation.tryParse(text);
                if (required == null) {
                    requiresIn.err("quests", "not a valid id: \"" + text + "\"");
                } else {
                    requires.add(required);
                    requiresIn.refTo("quests", "quests", required);
                }
            }
            atLeast = requires.size();
            JsonElement mode = requiresIn.raw("mode");
            if (mode != null && mode.isJsonObject()) {
                In modeIn = requiresIn.obj("mode");
                atLeast = Math.min(requires.size(), modeIn.integer("at_least", 1, 1));
                modeIn.done();
            } else if (mode != null) {
                String name = requiresIn.str("mode", "all");
                if (name.equals("any")) {
                    atLeast = Math.min(1, requires.size());
                } else if (!name.equals("all")) {
                    requiresIn.err("mode", "expected \"all\", \"any\" or {\"at_least\": N}");
                }
            }
            requiresIn.done();
        }

        boolean repeatable = false;
        int cooldown = 0;
        In repeatIn = in.obj("repeatable");
        if (repeatIn != null) {
            repeatable = repeatIn.bool("enabled", true);
            cooldown = repeatIn.integer("cooldown_seconds", 0, 0);
            repeatIn.done();
        }

        List<Phase> phases = new ArrayList<>();
        if (in.has("phases")) {
            for (In phaseIn : in.objs("phases")) {
                phases.add(phase(phaseIn.req("id"), phaseIn));
                phaseIn.done();
            }
        } else {
            // Shorthand: objectives at the top level are a single phase called "main".
            phases.add(phase("main", in));
        }
        if (phases.isEmpty()) {
            in.err("phases", "a quest needs at least one phase");
        }
        Set<String> phaseIds = new HashSet<>();
        Set<String> objectiveIds = new HashSet<>();
        for (Phase phase : phases) {
            if (!phaseIds.add(phase.id())) {
                in.err("phases", "duplicate phase id \"" + phase.id() + "\"");
            }
            for (Objective objective : phase.objectives()) {
                if (!objectiveIds.add(objective.id())) {
                    in.err("phases", "duplicate objective id \"" + objective.id() + "\"");
                }
            }
        }
        for (Phase phase : phases) {
            for (Transition transition : phase.transitions()) {
                String to = transition.to();
                if (!to.equals(Quest.COMPLETE) && !to.equals(Quest.FAIL) && !phaseIds.contains(to)) {
                    in.err("phases", "phase \"" + phase.id() + "\" has a transition to unknown phase \"" + to + "\"");
                }
            }
        }

        List<Reward> rewards = new ArrayList<>();
        Set<String> rewardIds = new HashSet<>();
        for (In rewardIn : in.objs("rewards")) {
            Reward reward = reward(rewardIn);
            rewardIn.done();
            if (reward != null) {
                if (!rewardIds.add(reward.id())) {
                    rewardIn.err("id", "duplicate reward id \"" + reward.id() + "\"");
                }
                rewards.add(reward);
            }
        }

        Quest quest = new Quest(id, chapter, title, description, atLeast, List.copyOf(requires),
                in.bool("optional", false), in.bool("hidden_until_unlocked", false), in.bool("auto_start", false),
                repeatable, cooldown, in.bool("auto_claim", false), List.copyOf(phases), List.copyOf(rewards));
        in.done();
        return quest;
    }

    private static Phase phase(String id, In in) {
        in.skip("markers", "on_enter", "on_exit");
        List<Objective> objectives = new ArrayList<>();
        for (In objectiveIn : in.objs("objectives")) {
            Objective objective = objective(objectiveIn);
            objectiveIn.done();
            if (objective != null) {
                objectives.add(objective);
            }
        }
        List<Transition> transitions = new ArrayList<>();
        for (In transitionIn : in.objs("transitions")) {
            transitionIn.skip("actions");
            Condition when = condition(transitionIn, "when");
            transitions.add(new Transition(transitionIn.req("to"), when == null ? ctx -> true : when,
                    transitionIn.str("label", null)));
            transitionIn.done();
        }
        return new Phase(id, in.text("story", false), List.copyOf(objectives), List.copyOf(transitions));
    }

    private static Objective objective(In in) {
        in.skip("icon", "hidden");
        String id = in.req("id");
        String type = in.req("type");
        Component text = in.text("text", false);
        int count = in.integer("count", 1, 1);
        boolean optional = in.bool("optional", false);
        Condition when = condition(in, "when");
        Target target = null;
        String name = null;
        boolean consume = false;
        ResourceLocation dimension = null;
        int x = 0, y = 0, z = 0, radius = 0;
        switch (type) {
            case "solace:kill" -> {
                target = in.target("entity", "entity_type", true);
                name = in.str("name", null);
            }
            case "solace:break_block", "solace:place_block" -> target = in.target("block", "block", true);
            case "solace:item" -> {
                target = in.target("item", "item", true);
                consume = in.bool("consume", false);
            }
            case "solace:reach_location" -> {
                dimension = in.id("dimension", true);
                radius = in.integer("radius", 8, 1);
                JsonElement pos = in.raw("pos");
                if (pos != null && pos.isJsonArray() && pos.getAsJsonArray().size() == 3) {
                    try {
                        x = pos.getAsJsonArray().get(0).getAsInt();
                        y = pos.getAsJsonArray().get(1).getAsInt();
                        z = pos.getAsJsonArray().get(2).getAsInt();
                    } catch (RuntimeException exception) {
                        in.err("pos", "expected [x, y, z] integers");
                    }
                } else {
                    in.err("pos", "expected [x, y, z]");
                }
                count = 1;
            }
            case "solace:condition" -> {
                if (when == null) {
                    in.err("when", "a condition objective needs \"when\"");
                }
                count = 1;
            }
            case "solace:checkmark" -> count = 1;
            default -> {
                in.err("type", "unknown objective type \"" + type + "\"");
                return null;
            }
        }
        return new Objective(id, type, text, count, optional, target, name, consume, dimension, x, y, z, radius, when);
    }

    private static Reward reward(In in) {
        in.skip("text", "icon");
        String id = in.req("id");
        String type = in.req("type");
        ResourceLocation target = null;
        int count = 0;
        CompoundTag nbt = null;
        String name = null;
        boolean on = false;
        switch (type) {
            case "solace:item" -> {
                target = in.id("item", true);
                if (target != null) {
                    in.known("item", "item", target);
                }
                count = in.integer("count", 1, 1);
                String snbt = in.str("nbt", null);
                if (snbt != null) {
                    try {
                        nbt = TagParser.parseTag(snbt);
                    } catch (Exception exception) {
                        in.err("nbt", "not valid SNBT: " + exception.getMessage());
                    }
                }
            }
            case "solace:xp" -> {
                on = in.has("levels");
                count = in.integer(on ? "levels" : "points", 0, 1);
                if (count == 0) {
                    in.err("points", "xp reward needs \"points\" or \"levels\"");
                }
            }
            case "solace:loot_table" -> target = in.id("table", true);
            case "solace:command" -> {
                name = in.req("command");
                count = in.integer("permission_level", 2, 0);
                if (count > 4) {
                    in.err("permission_level", "expected 0-4");
                }
            }
            case "solace:flag" -> {
                name = in.req("flag");
                on = in.bool("value", true);
            }
            case "solace:variable" -> {
                name = in.req("name");
                String op = in.str("op", "set");
                on = op.equals("add");
                if (!on && !op.equals("set")) {
                    in.err("op", "expected \"set\" or \"add\"");
                }
                count = in.integer("value", 0, Integer.MIN_VALUE);
            }
            case "solace:unlock" -> {
                on = in.has("chapter");
                target = in.ref(on ? "chapter" : "quest", on ? "chapters" : "quests", true);
            }
            default -> {
                in.err("type", "unknown reward type \"" + type + "\"");
                return null;
            }
        }
        return new Reward(id, type, target, count, nbt, name, on);
    }

    private static Condition condition(In in, String key) {
        In child = in.obj(key);
        return child == null ? null : Conditions.parse(child);
    }
}
