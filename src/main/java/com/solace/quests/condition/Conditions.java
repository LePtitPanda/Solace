package com.solace.quests.condition;

import com.solace.quests.content.In;
import com.solace.quests.content.Target;
import com.solace.quests.state.PlayerQuestData;
import com.solace.quests.state.QuestLogic;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses {"type": ...} condition objects. Reputation, entity and custom types arrive in later phases. */
public final class Conditions {
    private Conditions() {
    }

    /** Never returns null: an invalid condition is reported and evaluates to false. */
    public static Condition parse(In in) {
        Condition condition = read(in);
        in.done();
        return condition == null ? ctx -> false : condition;
    }

    private static Condition read(In in) {
        String type = in.req("type");
        switch (type) {
            case "solace:all", "solace:any" -> {
                List<Condition> of = new ArrayList<>();
                in.objs("of").forEach(child -> of.add(parse(child)));
                boolean all = type.equals("solace:all");
                return ctx -> all ? of.stream().allMatch(c -> c.test(ctx)) : of.stream().anyMatch(c -> c.test(ctx));
            }
            case "solace:not" -> {
                In child = in.obj("condition");
                if (child == null) {
                    in.err("condition", "missing required field");
                    return null;
                }
                Condition inner = parse(child);
                return ctx -> !inner.test(ctx);
            }
            case "solace:quest_state" -> {
                ResourceLocation quest = in.ref("quest", "quests", true);
                String state = in.req("state");
                QuestLogic.View view;
                try {
                    view = QuestLogic.View.valueOf(state.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    in.err("state", "expected one of locked, available, active, claimable, completed, failed");
                    return null;
                }
                return ctx -> QuestLogic.is(ctx, quest, view);
            }
            case "solace:phase" -> {
                ResourceLocation quest = in.ref("quest", "quests", true);
                String phase = in.req("phase");
                boolean reached = in.bool("reached", false);
                return ctx -> {
                    PlayerQuestData.QuestState state = ctx.data().quests.get(quest);
                    return state != null && (state.status == PlayerQuestData.Status.ACTIVE && phase.equals(state.phase)
                            || reached && state.history.stream().anyMatch(entry -> phase.equals(entry.phase())));
                };
            }
            case "solace:objectives_complete" -> {
                List<String> objectives = in.has("objectives") ? in.strs("objectives") : null;
                return ctx -> QuestLogic.objectivesComplete(ctx, objectives);
            }
            case "solace:flag" -> {
                String flag = in.req("flag");
                boolean value = in.bool("value", true);
                return ctx -> ctx.data().flags.contains(flag) == value;
            }
            case "solace:variable" -> {
                String name = in.req("name");
                String op = in.str("op", "==");
                int value = in.integer("value", 0, Integer.MIN_VALUE);
                if (!List.of("==", "!=", "<", "<=", ">", ">=").contains(op)) {
                    in.err("op", "expected one of == != < <= > >=");
                    return null;
                }
                return ctx -> compare(ctx.data().variables.getOrDefault(name, 0), op, value);
            }
            case "solace:has_item" -> {
                Target item = in.target("item", "item", true);
                int count = in.integer("count", 1, 1);
                boolean held = in.bool("held", false);
                return item == null ? null : ctx -> ctx.countItem(item, held) >= count;
            }
            case "solace:dimension" -> {
                ResourceLocation dimension = in.id("dimension", true);
                return dimension == null ? null : ctx -> dimension.toString().equals(ctx.dimension());
            }
            case "solace:time" -> {
                int from = in.integer("from", 0, 0);
                int to = in.integer("to", 23999, 0);
                // from > to wraps over midnight, e.g. 13000..1000 is "at night".
                return ctx -> {
                    long time = ctx.dayTime();
                    return from <= to ? time >= from && time <= to : time >= from || time <= to;
                };
            }
            case "solace:predicate" -> {
                ResourceLocation predicate = in.id("predicate", true);
                return predicate == null ? null : ctx -> ctx.predicate(predicate);
            }
            default -> {
                in.err("type", "unknown condition type \"" + type + "\"");
                return null;
            }
        }
    }

    public static boolean compare(int left, String op, int right) {
        return switch (op) {
            case "==" -> left == right;
            case "!=" -> left != right;
            case "<" -> left < right;
            case "<=" -> left <= right;
            case ">" -> left > right;
            default -> left >= right;
        };
    }
}
