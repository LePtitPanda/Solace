package com.solace.quests.content;

import com.solace.quests.condition.Condition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Content kinds that are not quests: quest loot, recipe locks and trade locks. */
public final class Locks {
    private Locks() {
    }

    /**
     * {@code loot/*.json}: items added to generated loot while {@code when} holds for the player who caused it.
     * Empty filters match everything; {@code tables} entries may end in "*" (prefix).
     */
    public record QuestLoot(ResourceLocation id, List<String> tables, Target structure, Target entity, Target block,
                            Condition when, List<Drop> items, float chance, boolean once) {
    }

    public record Drop(ResourceLocation item, int min, int max, CompoundTag nbt, float chance) {
    }

    /**
     * {@code recipe_locks/*.json}: these recipes are hidden and uncraftable for a player until the lock is
     * unlocked: the flag "recipes:&lt;id&gt;" (set by solace:unlock_recipes or by using {@code item}), or
     * {@code unlockWhen}.
     */
    public record RecipeLock(ResourceLocation id, List<String> recipes, List<Target> outputs,
                             List<ResourceLocation> types, Condition unlockWhen, ResourceLocation item,
                             boolean consumeItem, Component learnText) {
    }

    /**
     * {@code trades/*.json}: offers of matching merchants are disabled (shown out of stock) or hidden for a
     * player until {@code unlockWhen} holds. {@code when} picks the merchant (solace:entity sees it).
     */
    public record TradeLock(ResourceLocation id, Target entity, Condition when, boolean all, List<Offer> offers,
                            Condition unlockWhen, boolean hide) {
    }

    /** Null fields match anything. */
    public record Offer(Target result, Target cost) {
    }

    static QuestLoot loot(ResourceLocation id, In in) {
        List<Drop> items = new ArrayList<>();
        for (In itemIn : in.objs("items")) {
            ResourceLocation item = itemIn.id("item", true);
            if (item != null) {
                itemIn.known("item", "item", item);
            }
            int count = itemIn.integer("count", 1, 1);
            int min = itemIn.integer("min", count, 1);
            int max = itemIn.integer("max", Math.max(min, count), 1);
            if (max < min) {
                itemIn.err("max", "max is smaller than min");
            }
            items.add(new Drop(item, min, Math.max(min, max), nbt(itemIn), chance(itemIn)));
            itemIn.done();
        }
        if (items.isEmpty()) {
            in.err("items", "loot needs at least one item");
        }
        QuestLoot loot = new QuestLoot(id, List.copyOf(in.strs("tables")), in.target("structure", "structure", false),
                in.target("entity", "entity_type", false), in.target("block", "block", false),
                QuestParser.condition(in, "when"), List.copyOf(items), chance(in), in.bool("once", false));
        in.done();
        return loot;
    }

    static RecipeLock recipeLock(ResourceLocation id, In in) {
        List<Target> outputs = new ArrayList<>();
        for (String text : in.strs("outputs")) {
            boolean tag = text.startsWith("#");
            ResourceLocation output = ResourceLocation.tryParse(tag ? text.substring(1) : text);
            if (output == null) {
                in.err("outputs", "not a valid id: \"" + text + "\"");
            } else {
                outputs.add(new Target(tag, output));
            }
        }
        List<ResourceLocation> types = new ArrayList<>();
        for (String text : in.has("types") ? in.strs("types") : List.of("minecraft:crafting")) {
            ResourceLocation type = ResourceLocation.tryParse(text);
            if (type == null) {
                in.err("types", "not a valid id: \"" + text + "\"");
            } else {
                types.add(type);
            }
        }
        List<String> recipes = List.copyOf(in.strs("recipes"));
        if (recipes.isEmpty() && outputs.isEmpty()) {
            in.err("recipes", "a recipe lock needs \"recipes\" or \"outputs\"");
        }
        ResourceLocation item = in.id("item", false);
        if (item != null) {
            in.known("item", "item", item);
        }
        RecipeLock lock = new RecipeLock(id, recipes, List.copyOf(outputs), List.copyOf(types),
                QuestParser.condition(in, "unlock_when"), item, in.bool("consume_item", false),
                in.text("learn_text", false));
        in.done();
        return lock;
    }

    static TradeLock tradeLock(ResourceLocation id, In in) {
        Target entity = in.target("entity", "entity_type", false);
        boolean all = false;
        List<Offer> offers = new ArrayList<>();
        com.google.gson.JsonElement raw = in.raw("offers");
        if (raw != null && raw.isJsonPrimitive() && raw.getAsString().equals("all")) {
            all = true;
            in.skip("offers");
        } else {
            for (In offerIn : in.objs("offers")) {
                Target result = offerIn.target("result", "item", false);
                Target cost = offerIn.target("cost", "item", false);
                if (result == null && cost == null) {
                    offerIn.err("result", "an offer needs \"result\" or \"cost\"");
                }
                offers.add(new Offer(result, cost));
                offerIn.done();
            }
            if (offers.isEmpty()) {
                in.err("offers", "expected \"all\" or a list of {\"result\", \"cost\"}");
            }
        }
        String mode = in.str("mode", "disable");
        if (!mode.equals("disable") && !mode.equals("hide")) {
            in.err("mode", "expected \"disable\" or \"hide\"");
        }
        Condition unlock = QuestParser.condition(in, "unlock_when");
        if (unlock == null) {
            in.err("unlock_when", "missing required field");
        }
        TradeLock lock = new TradeLock(id, entity == null ? new Target(false,
                new ResourceLocation("minecraft", "villager")) : entity, QuestParser.condition(in, "when"), all,
                List.copyOf(offers), unlock == null ? ctx -> false : unlock, mode.equals("hide"));
        in.done();
        return lock;
    }

    private static float chance(In in) {
        com.google.gson.JsonElement raw = in.raw("chance");
        if (raw == null) {
            return 1F;
        }
        try {
            float chance = raw.getAsFloat();
            if (chance < 0 || chance > 1) {
                in.err("chance", "expected 0 to 1");
            }
            return chance;
        } catch (RuntimeException exception) {
            in.err("chance", "expected a number from 0 to 1");
            return 1F;
        }
    }

    private static CompoundTag nbt(In in) {
        String snbt = in.str("nbt", null);
        if (snbt == null) {
            return null;
        }
        try {
            return TagParser.parseTag(snbt);
        } catch (Exception exception) {
            in.err("nbt", "not valid SNBT: " + exception.getMessage());
            return null;
        }
    }
}
