package com.solace.quests.condition;

import com.solace.quests.content.Faction;
import com.solace.quests.content.In;
import com.solace.quests.content.Target;
import com.solace.quests.state.PlayerQuestData;
import com.solace.quests.state.QuestLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses {"type": ...} condition objects. */
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
                String snbt = in.str("nbt", null);
                if (item != null && snbt != null) {
                    try {
                        item = item.withNbt(TagParser.parseTag(snbt));
                    } catch (Exception exception) {
                        in.err("nbt", "not valid SNBT: " + exception.getMessage());
                    }
                }
                Target wanted = item;
                int count = in.integer("count", 1, 1);
                boolean held = in.bool("held", false);
                return wanted == null ? null : ctx -> ctx.countItem(wanted, held) >= count;
            }
            case "solace:area" -> {
                String area = in.req("area");
                return ctx -> ctx.inArea(area);
            }
            case "solace:near_npc" -> {
                ResourceLocation npc = in.ref("npc", "npcs", true);
                int radius = in.integer("radius", 8, 1);
                return npc == null ? null : ctx -> ctx.nearNpc(npc, radius);
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
            case "solace:reputation" -> {
                ResourceLocation faction = in.ref("faction", "factions", true);
                String op = op(in);
                int value = in.integer("value", 0, Integer.MIN_VALUE);
                return op == null ? null : reputation(faction, op, value);
            }
            case "solace:faction_rank" -> {
                ResourceLocation faction = in.ref("faction", "factions", true);
                String op = op(in);
                String rank = in.req("rank");
                return op == null ? null : factionRank(faction, op, rank);
            }
            case "solace:biome" -> {
                Target biome = in.target("biome", "biome", true);
                return biome == null ? null : ctx -> ctx.inBiome(biome);
            }
            case "solace:structure" -> {
                Target structure = in.target("structure", "structure", true);
                String piece = in.str("piece", null);
                return structure == null ? null : ctx -> ctx.inStructure(structure, piece);
            }
            case "solace:advancement" -> {
                ResourceLocation advancement = in.id("advancement", true);
                return advancement == null ? null : ctx -> ctx.hasAdvancement(advancement);
            }
            case "solace:entity" -> {
                String name = in.str("name_contains", null);
                ResourceLocation profession = in.id("profession", false);
                CompoundTag nbt = null;
                String snbt = in.str("nbt", null);
                if (snbt != null) {
                    try {
                        nbt = TagParser.parseTag(snbt);
                    } catch (Exception exception) {
                        in.err("nbt", "not valid SNBT: " + exception.getMessage());
                    }
                }
                CompoundTag wanted = nbt;
                return ctx -> ctx.entity instanceof Entity entity
                        && (name == null || entity.getName().getString().contains(name))
                        && (profession == null || entity instanceof Villager villager && profession.equals(
                        ForgeRegistries.VILLAGER_PROFESSIONS.getKey(villager.getVillagerData().getProfession())))
                        && (wanted == null || NbtUtils.compareNbt(wanted, entity.saveWithoutId(new CompoundTag()), true));
            }
            case "solace:custom" -> {
                String handler = in.req("handler");
                com.google.gson.JsonElement raw = in.raw("data");
                String data = raw == null ? "null" : raw.toString();
                return ctx -> ctx.custom(handler, data);
            }
            default -> {
                in.err("type", "unknown condition type \"" + type + "\"");
                return null;
            }
        }
    }

    private static String op(In in) {
        String op = in.str("op", ">=");
        if (!List.of("==", "!=", "<", "<=", ">", ">=").contains(op)) {
            in.err("op", "expected one of == != < <= > >=");
            return null;
        }
        return op;
    }

    public static Condition reputation(ResourceLocation faction, String op, int value) {
        return ctx -> faction != null && compare(ctx.reputation(faction), op, value);
    }

    /** Compares rank positions, so "&gt;= friendly" is true for every rank above it too. */
    public static Condition factionRank(ResourceLocation factionId, String op, String rank) {
        return ctx -> {
            Faction faction = factionId == null ? null : ctx.content().factions.get(factionId);
            int wanted = faction == null ? -1 : faction.rankIndex(rank);
            return wanted >= 0 && compare(faction.rankIndex(ctx.reputation(factionId)), op, wanted);
        };
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
