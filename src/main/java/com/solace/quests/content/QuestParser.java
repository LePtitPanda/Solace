package com.solace.quests.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solace.world.gates.StructureGateConfig;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** JSON to definitions. Every problem is reported through {@link In}; nothing throws. */
public final class QuestParser {
    /** Friendlier names accepted for effect types when used as dialogue/phase actions. */
    private static final Map<String, String> ALIASES = Map.of("solace:give_item", "solace:item",
            "solace:set_flag", "solace:flag", "solace:set_variable", "solace:variable");

    private QuestParser() {
    }

    private static void noId(In in) {
        if (in.has("id")) {
            in.warn("id", "ignored: the id comes from the file location");
            in.skip("id");
        }
    }

    static String icon(In in) {
        In iconIn = in.obj("icon");
        if (iconIn == null) {
            return null;
        }
        ResourceLocation item = iconIn.id("item", false);
        ResourceLocation texture = iconIn.id("texture", false);
        if (item != null) {
            iconIn.known("item", "item", item);
        } else if (texture == null) {
            iconIn.err("", "expected {\"item\": id} or {\"texture\": id}");
        }
        iconIn.done();
        return item != null ? "item:" + item : texture != null ? "texture:" + texture : null;
    }

    public static Chapter chapter(ResourceLocation id, In in) {
        noId(in);
        Chapter chapter = new Chapter(id, in.text("title", true), in.text("description", false), icon(in),
                in.integer("order", 0, Integer.MIN_VALUE), condition(in, "unlock"),
                in.bool("hidden_until_unlocked", false));
        in.done();
        return chapter;
    }

    public static Quest quest(ResourceLocation id, In in) {
        noId(in);
        ResourceLocation chapter = in.ref("chapter", "chapters", true);
        Component title = in.text("title", true);
        Component description = in.text("description", false);
        int x = 0, y = 0;
        In position = in.obj("position");
        if (position != null) {
            x = position.integer("x", 0, Integer.MIN_VALUE);
            y = position.integer("y", 0, Integer.MIN_VALUE);
            position.done();
        }

        List<ResourceLocation> requires = new ArrayList<>();
        int atLeast = 0;
        In requiresIn = in.obj("requires");
        if (requiresIn != null) {
            for (String text : requiresIn.strs("quests")) {
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
            Reward reward = effect(rewardIn, true);
            rewardIn.done();
            if (reward != null) {
                if (!rewardIds.add(reward.id())) {
                    rewardIn.err("id", "duplicate reward id \"" + reward.id() + "\"");
                }
                rewards.add(reward);
            }
        }
        String onFail = in.str("on_fail", "keep");
        if (!onFail.equals("keep") && !onFail.equals("reset")) {
            in.err("on_fail", "expected \"keep\" or \"reset\"");
        }

        // "hidden_until": the quest stays out of the book until enough dependencies or objectives are done.
        int hiddenDeps = 0, hiddenObjectives = 0;
        String hiddenUntil = in.str("hidden_until", "none");
        int hiddenCount = in.integer("hidden_count", 1, 1);
        switch (hiddenUntil) {
            case "none" -> {
            }
            case "one_dependency" -> hiddenDeps = 1;
            case "all_dependencies" -> hiddenDeps = Integer.MAX_VALUE;
            case "n_dependencies" -> hiddenDeps = hiddenCount;
            case "one_objective" -> hiddenObjectives = 1;
            case "all_objectives" -> hiddenObjectives = Integer.MAX_VALUE;
            case "n_objectives" -> hiddenObjectives = hiddenCount;
            default -> in.err("hidden_until", "expected none, one_dependency, all_dependencies, n_dependencies, "
                    + "one_objective, all_objectives or n_objectives");
        }

        Quest quest = new Quest(id, chapter, title, description, icon(in), x, y, atLeast, List.copyOf(requires),
                in.bool("optional", false), in.bool("hidden_until_unlocked", false), hiddenDeps, hiddenObjectives,
                in.bool("auto_start", false),
                repeatable, cooldown, in.bool("auto_claim", false), in.integer("time_limit_seconds", 0, 0),
                onFail.equals("reset"), in.bool("abandonable", true), condition(in, "visible_when"),
                // In the single-phase shorthand the top-level markers already belong to that phase.
                in.has("phases") ? markers(in, "markers") : List.of(), List.copyOf(phases), List.copyOf(rewards));
        in.done();
        return quest;
    }

    private static Phase phase(String id, In in) {
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
            Condition when = condition(transitionIn, "when");
            transitions.add(new Transition(transitionIn.req("to"), when == null ? ctx -> true : when,
                    transitionIn.str("label", null), effects(transitionIn, "actions")));
            transitionIn.done();
        }
        return new Phase(id, in.text("story", false), List.copyOf(objectives), List.copyOf(transitions),
                effects(in, "on_enter"), markers(in, "markers"));
    }

    private static Objective objective(In in) {
        String id = in.req("id");
        String type = in.req("type");
        Component text = in.text("text", false);
        int count = in.integer("count", 1, 1);
        boolean optional = in.bool("optional", false);
        boolean hidden = in.bool("hidden", false);
        Condition when = condition(in, "when");
        Condition auto = null;
        Target target = null;
        String name = null;
        boolean consume = false;
        ResourceLocation ref = null;
        int x = 0, y = 0, z = 0, radius = 0;
        switch (type) {
            case "solace:kill", "solace:interact_entity" -> {
                target = in.target("entity", "entity_type", true);
                name = in.str("name", null);
            }
            case "solace:break_block", "solace:place_block", "solace:interact_block" ->
                    target = in.target("block", "block", true);
            case "solace:item" -> {
                target = in.target("item", "item", true);
                consume = in.bool("consume", false);
            }
            case "solace:deliver" -> {
                target = in.target("item", "item", true);
                ref = in.ref("npc", "npcs", true);
            }
            case "solace:craft", "solace:smelt", "solace:use_item" -> target = in.target("item", "item", true);
            case "solace:reach_location" -> {
                ref = in.id("dimension", true);
                radius = in.integer("radius", 8, 1);
                int[] pos = pos(in);
                x = pos[0];
                y = pos[1];
                z = pos[2];
                count = 1;
            }
            case "solace:dialogue_node" -> {
                ref = in.ref("dialogue", "dialogues", true);
                name = in.req("node");
                count = 1;
            }
            case "solace:find_structure" -> {
                target = in.target("structure", "structure", true);
                Target structure = target;
                auto = structure == null ? null : ctx -> ctx.inStructure(structure);
            }
            case "solace:enter_biome" -> {
                target = in.target("biome", "biome", true);
                Target biome = target;
                auto = biome == null ? null : ctx -> ctx.inBiome(biome);
            }
            case "solace:enter_dimension" -> {
                ref = in.id("dimension", true);
                String dimension = String.valueOf(ref);
                auto = ctx -> dimension.equals(ctx.dimension());
            }
            case "solace:advancement" -> {
                ref = in.id("advancement", true);
                ResourceLocation advancement = ref;
                auto = ctx -> ctx.hasAdvancement(advancement);
            }
            case "solace:reputation" -> {
                ref = in.ref("faction", "factions", true);
                auto = Conditions.reputation(ref, ">=", in.integer("min", 0, Integer.MIN_VALUE));
            }
            case "solace:faction_rank" -> {
                ref = in.ref("faction", "factions", true);
                name = in.req("rank");
                auto = Conditions.factionRank(ref, ">=", name);
            }
            case "solace:condition" -> {
                if (when == null) {
                    in.err("when", "a condition objective needs \"when\"");
                }
            }
            case "solace:checkmark" -> {
            }
            case "solace:custom" -> in.skip("handler", "data");
            default -> {
                in.err("type", "unknown objective type \"" + type + "\"");
                return null;
            }
        }
        if (Quest.AUTO.contains(type)) {
            count = 1;
            if (auto != null && when != null) {
                Condition gate = when, check = auto;
                auto = ctx -> gate.test(ctx) && check.test(ctx);
            }
            when = auto != null ? auto : when;
        }
        return new Objective(id, type, text, icon(in), count, optional, hidden, target, name, consume, ref, x, y, z,
                radius, when);
    }

    private static int[] pos(In in) {
        JsonElement pos = in.raw("pos");
        if (pos != null && pos.isJsonArray() && pos.getAsJsonArray().size() == 3) {
            try {
                return new int[]{pos.getAsJsonArray().get(0).getAsInt(), pos.getAsJsonArray().get(1).getAsInt(),
                        pos.getAsJsonArray().get(2).getAsInt()};
            } catch (RuntimeException exception) {
                in.err("pos", "expected [x, y, z] integers");
            }
        } else {
            in.err("pos", "expected [x, y, z]");
        }
        return new int[3];
    }

    static List<Reward> effects(In in, String key) {
        List<Reward> list = new ArrayList<>();
        for (In effectIn : in.objs(key)) {
            Reward effect = effect(effectIn, false);
            effectIn.done();
            if (effect != null) {
                list.add(effect);
            }
        }
        return List.copyOf(list);
    }

    /** Rewards need an id (claim tracking is keyed by it); actions do not. */
    private static Reward effect(In in, boolean reward) {
        String id = reward ? in.req("id") : in.str("id", "");
        String type = in.req("type");
        type = ALIASES.getOrDefault(type, type);
        Component text = in.text("text", false);
        ResourceLocation target = null;
        int count = 0;
        CompoundTag nbt = null;
        String name = null;
        String data = null;
        boolean on = false;
        switch (type) {
            case "solace:item", "solace:take_item" -> {
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
                on = in.bool("optional", false);
            }
            case "solace:xp" -> {
                on = in.has("levels");
                count = in.integer(on ? "levels" : "points", 0, 1);
                if (count == 0) {
                    in.err("points", "xp needs \"points\" or \"levels\"");
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
            case "solace:reputation" -> {
                target = in.ref("faction", "factions", true);
                count = in.integer("amount", 0, Integer.MIN_VALUE);
            }
            case "solace:start_quest", "solace:complete_quest", "solace:fail_quest" ->
                    target = in.ref("quest", "quests", true);
            case "solace:set_phase" -> {
                target = in.ref("quest", "quests", true);
                name = in.req("phase");
            }
            case "solace:complete_objective" -> {
                target = in.ref("quest", "quests", true);
                name = in.req("objective");
            }
            case "solace:dialogue", "solace:open_dialogue" -> {
                type = "solace:dialogue";
                target = in.ref("dialogue", "dialogues", true);
            }
            case "solace:add_marker", "solace:remove_marker" -> target = in.ref("marker", "markers", true);
            case "solace:deliver" -> target = in.ref("npc", "npcs", true);
            case "solace:close" -> {
            }
            case "solace:custom" -> {
                name = in.req("handler");
                JsonElement raw = in.raw("data");
                data = raw == null ? "null" : raw.toString();
            }
            case "solace:open_structure_gate", "solace:close_structure_gate" -> {
                Target structure = in.target("structure", "structure", true);
                name = structure == null ? "" : structure.toString();
                if (type.equals("solace:open_structure_gate")) {
                    data = in.str("mode", null);
                    if (data != null && StructureGateConfig.Mode.parse(data) == null) {
                        in.err("mode", "expected \"once\" or \"infinite\"");
                    }
                }
            }
            case "solace:give_structure_map", "solace:give_structure_compass" -> {
                Target structure = in.target("structure", "structure", true);
                name = structure == null ? "" : structure.toString();
                boolean map = type.equals("solace:give_structure_map");
                if (!map) {
                    target = in.id("item", false);
                    if (target == null) {
                        target = new ResourceLocation("minecraft", "compass");
                    }
                    in.known("item", "item", target);
                }
                in.text("name", false);
                JsonObject options = new JsonObject();
                for (String key : map ? new String[]{"name", "lore", "decoration", "search_radius"}
                        : new String[]{"name", "lore", "sound", "search_radius"}) {
                    if (in.has(key)) {
                        options.add(key, in.raw(key));
                    }
                }
                in.integer("search_radius", 100, 1);
                String decoration = map ? in.str("decoration", null) : null;
                if (decoration != null && !EnumSet.allOf(MapDecoration.Type.class).stream()
                        .anyMatch(value -> value.name().equalsIgnoreCase(decoration))) {
                    in.err("decoration", "unknown map decoration \"" + decoration + "\"");
                }
                if (!map && in.id("sound", false) == null && in.has("sound")) {
                    in.err("sound", "not a valid sound id");
                }
                data = options.toString();
            }
            default -> {
                in.err("type", "unknown " + (reward ? "reward" : "action") + " type \"" + type + "\"");
                return null;
            }
        }
        return new Reward(id, type, text, icon(in), target, count, nbt, name, on, data);
    }

    /** Each entry is a markers/ file id (string) or an inline marker object. */
    static List<Marker> markers(In in, String key) {
        List<Marker> list = new ArrayList<>();
        JsonElement raw = in.raw(key);
        if (raw == null) {
            return list;
        }
        if (!raw.isJsonArray()) {
            in.err(key, "expected a list");
            return list;
        }
        for (int i = 0; i < raw.getAsJsonArray().size(); i++) {
            JsonElement entry = raw.getAsJsonArray().get(i);
            In itemIn = in.child(key + "[" + i + "]", entry);
            if (itemIn != null) {
                list.add(marker(itemIn));
                itemIn.done();
            } else if (entry.isJsonPrimitive() && ResourceLocation.tryParse(entry.getAsString()) != null) {
                ResourceLocation ref = ResourceLocation.tryParse(entry.getAsString());
                in.refTo(key + "[" + i + "]", "markers", ref);
                list.add(new Marker(ref, "ref", null, 0, 0, 0, null, null, null, null, null, false));
            } else {
                in.err(key + "[" + i + "]", "expected a marker id or a marker object");
            }
        }
        return List.copyOf(list);
    }

    public static Marker marker(In in) {
        String kind = in.str("target", "position");
        ResourceLocation dimension = null;
        int[] pos = new int[3];
        Target structure = null;
        ResourceLocation npc = null;
        String objective = null;
        switch (kind) {
            case "position" -> {
                dimension = in.id("dimension", true);
                pos = pos(in);
            }
            case "structure" -> structure = in.target("structure", "structure", true);
            case "npc" -> npc = in.ref("npc", "npcs", true);
            case "objective" -> objective = in.req("objective");
            default -> in.err("target", "expected position, structure, npc or objective");
        }
        return new Marker(null, kind, dimension, pos[0], pos[1], pos[2], structure, npc, objective,
                in.text("label", false), in.str("color", "gold"), in.bool("beam", false));
    }

    public static Marker markerFile(ResourceLocation id, In in) {
        noId(in);
        Marker marker = marker(in);
        if (marker.kind().equals("objective")) {
            in.err("target", "an \"objective\" marker only works inline in a quest or phase");
        }
        in.done();
        return marker;
    }

    public static Faction faction(ResourceLocation id, In in) {
        noId(in);
        int color = 0xD4A843;
        String hex = in.str("color", null);
        if (hex != null) {
            try {
                color = Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException exception) {
                in.err("color", "expected \"#RRGGBB\"");
            }
        }
        List<Faction.Rank> ranks = new ArrayList<>();
        for (In rankIn : in.objs("ranks")) {
            ranks.add(new Faction.Rank(rankIn.req("id"), rankIn.text("name", true),
                    rankIn.integer("at", 0, Integer.MIN_VALUE)));
            rankIn.done();
        }
        ranks.sort(Comparator.comparingInt(Faction.Rank::at));
        if (ranks.isEmpty()) {
            in.err("ranks", "a faction needs at least one rank");
        }
        List<Faction.Relation> relations = new ArrayList<>();
        for (In relationIn : in.objs("relations")) {
            ResourceLocation other = relationIn.ref("faction", "factions", true);
            JsonElement factor = relationIn.raw("factor");
            if (factor == null || !factor.isJsonPrimitive() || !factor.getAsJsonPrimitive().isNumber()) {
                relationIn.err("factor", "expected a number, e.g. -0.5");
            } else if (other != null) {
                relations.add(new Faction.Relation(other, factor.getAsDouble()));
            }
            relationIn.done();
        }
        boolean gossip = false;
        int radius = 48;
        In source = in.obj("source");
        if (source != null) {
            gossip = source.req("type").equals("solace:villager_gossip");
            if (!gossip) {
                source.err("type", "the only source type is \"solace:villager_gossip\"");
            }
            radius = source.integer("radius", 48, 1);
            source.done();
        }
        int min = in.integer("min", -1000, Integer.MIN_VALUE);
        int max = in.integer("max", 1000, Integer.MIN_VALUE);
        if (min > max) {
            in.err("min", "min is greater than max");
        }
        Faction faction = new Faction(id, in.text("name", true), color, icon(in),
                in.integer("default", 0, Integer.MIN_VALUE), min, max, List.copyOf(ranks), List.copyOf(relations),
                gossip, radius);
        in.done();
        return faction;
    }

    static Condition condition(In in, String key) {
        In child = in.obj(key);
        return child == null ? null : Conditions.parse(child);
    }
}
