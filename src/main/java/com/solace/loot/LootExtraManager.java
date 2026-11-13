package com.solace.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovery and per-table control for items appended by Forge global loot modifiers. */
public final class LootExtraManager {
    private static final Pattern YAML_ENTRY = Pattern.compile(
            "^(?:'([^']+)'|\"([^\"]+)\"|(.+?)):\\s*([0-9]*\\.?[0-9]+)\\s*$");
    private static final Map<ResourceLocation, List<Extra>> DISCOVERY_CACHE = new HashMap<>();

    private LootExtraManager() {
    }

    public record Extra(ResourceLocation originalItem, ItemStack stack, int chanceBasisPoints) {
    }

    public static synchronized void clearDiscoveryCache() {
        DISCOVERY_CACHE.clear();
    }

    public static synchronized void clearDiscoveryCache(ResourceLocation table) {
        DISCOVERY_CACHE.remove(table);
    }

    public static synchronized List<Extra> discover(ResourceLocation table) {
        return DISCOVERY_CACHE.computeIfAbsent(table, LootExtraManager::discoverUncached);
    }

    private static List<Extra> discoverUncached(ResourceLocation table) {
        Map<ResourceLocation, Extra> result = new LinkedHashMap<>();
        scanRelicDirectory(FMLPaths.CONFIGDIR.get().resolve("relics/relics"), "relics", table, result);
        scanRelicDirectory(FMLPaths.CONFIGDIR.get().resolve("morerelics/relics"), "morerelics", table, result);

        JsonElement observedElement = LootEditorConfig.observedExtras().get(table.toString());
        if (observedElement != null && observedElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : observedElement.getAsJsonObject().entrySet()) {
                ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
                if (itemId != null && entry.getValue().isJsonObject()) {
                    ItemStack stack = readStack(entry.getValue().getAsJsonObject());
                    if (!stack.isEmpty()) {
                        result.putIfAbsent(itemId, new Extra(itemId, stack, -1));
                    }
                }
            }
        }

        JsonElement rulesElement = LootEditorConfig.extraRules().get(table.toString());
        if (rulesElement != null && rulesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : rulesElement.getAsJsonObject().entrySet()) {
                ResourceLocation original = ResourceLocation.tryParse(entry.getKey());
                if (original == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject rule = entry.getValue().getAsJsonObject();
                ItemStack stack = readStack(rule);
                int chance = rule.has("chance") ? rule.get("chance").getAsInt()
                        : result.containsKey(original) ? result.get(original).chanceBasisPoints() : -1;
                if (!stack.isEmpty()) {
                    result.put(original, new Extra(original, stack, chance));
                }
            }
        }
        return List.copyOf(result.values());
    }

    private static void scanRelicDirectory(Path directory, String namespace, ResourceLocation table,
                                           Map<ResourceLocation, Extra> output) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml")).forEach(path -> {
                String fileName = path.getFileName().toString();
                ResourceLocation itemId = ResourceLocation.tryParse(
                        namespace + ":" + fileName.substring(0, fileName.length() - ".yaml".length()));
                if (itemId == null) {
                    return;
                }
                double chance = relicChance(path, table.toString());
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (chance >= 0.0D && item != Items.AIR) {
                    output.put(itemId, new Extra(itemId, new ItemStack(item),
                            Math.max(0, Math.min(10_000, (int) Math.round(chance * 10_000.0D)))));
                }
            });
        } catch (IOException ignored) {
            // Optional compatibility: observed runtime additions still cover unreadable configs.
        }
    }

    private static double relicChance(Path path, String table) {
        try {
            boolean inLootData = false;
            boolean inEntries = false;
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.equals("lootData:")) {
                    inLootData = true;
                    inEntries = false;
                    continue;
                }
                if (!inLootData) {
                    continue;
                }
                int indent = line.length() - line.stripLeading().length();
                if (indent == 0 && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    break;
                }
                if (trimmed.equals("entries:")) {
                    inEntries = true;
                    continue;
                }
                if (!inEntries || trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                Matcher matcher = YAML_ENTRY.matcher(trimmed);
                if (!matcher.matches()) {
                    continue;
                }
                String expression = matcher.group(1) != null ? matcher.group(1)
                        : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                try {
                    if (table.matches(expression)) {
                        return Double.parseDouble(matcher.group(4));
                    }
                } catch (RuntimeException ignored) {
                    if (table.equals(expression)) {
                        return Double.parseDouble(matcher.group(4));
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return -1.0D;
    }

    public static synchronized void setRule(ResourceLocation table, ResourceLocation originalItem,
                                            ItemStack replacement, int chanceBasisPoints) {
        JsonObject tables = LootEditorConfig.extraRules();
        JsonObject rules = tables.has(table.toString()) && tables.get(table.toString()).isJsonObject()
                ? tables.getAsJsonObject(table.toString()) : new JsonObject();
        JsonObject rule = writeStack(replacement);
        rule.addProperty("chance", Math.max(0, Math.min(10_000, chanceBasisPoints)));
        rules.add(originalItem.toString(), rule);
        tables.add(table.toString(), rules);
        DISCOVERY_CACHE.remove(table);
    }

    public static ObjectArrayList<ItemStack> applyModifiers(ResourceLocation table,
                                                             ObjectArrayList<ItemStack> generated,
                                                             LootContext context) {
        if (table == null || LootEditorManager.suppressesModifiers(table)) {
            return generated;
        }
        List<ItemStack> base = generated.stream().map(ItemStack::copy).toList();
        ObjectArrayList<ItemStack> modified = ForgeHooks.modifyLoot(table, generated, context);
        observeAdditions(table, base, modified);
        applyRules(table, base, modified, context);
        com.solace.quests.locks.QuestLootRules.apply(table, modified, context);
        return modified;
    }

    private static synchronized void observeAdditions(ResourceLocation table, List<ItemStack> base,
                                                       List<ItemStack> modified) {
        Map<ResourceLocation, Integer> baseCounts = counts(base);
        Map<ResourceLocation, Integer> finalCounts = counts(modified);
        JsonObject observedTables = LootEditorConfig.observedExtras();
        JsonObject observed = observedTables.has(table.toString())
                && observedTables.get(table.toString()).isJsonObject()
                ? observedTables.getAsJsonObject(table.toString()) : new JsonObject();
        boolean changed = false;
        for (ItemStack stack : modified) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (finalCounts.getOrDefault(itemId, 0) <= baseCounts.getOrDefault(itemId, 0)
                    || observed.has(itemId.toString())) {
                continue;
            }
            observed.add(itemId.toString(), writeStack(stack));
            changed = true;
        }
        if (changed) {
            observedTables.add(table.toString(), observed);
            DISCOVERY_CACHE.remove(table);
            LootEditorConfig.save();
        }
    }

    /** Merges items found by the one-time startup probe and persists them in one write. */
    public static synchronized int recordDetected(Map<ResourceLocation, ? extends Map<ResourceLocation, ItemStack>> detected) {
        int added = 0;
        JsonObject observedTables = LootEditorConfig.observedExtras();
        for (Map.Entry<ResourceLocation, ? extends Map<ResourceLocation, ItemStack>> tableEntry : detected.entrySet()) {
            String tableKey = tableEntry.getKey().toString();
            JsonObject observed = observedTables.has(tableKey) && observedTables.get(tableKey).isJsonObject()
                    ? observedTables.getAsJsonObject(tableKey) : new JsonObject();
            boolean tableChanged = false;
            for (Map.Entry<ResourceLocation, ItemStack> itemEntry : tableEntry.getValue().entrySet()) {
                String itemKey = itemEntry.getKey().toString();
                if (!observed.has(itemKey) && !itemEntry.getValue().isEmpty()) {
                    observed.add(itemKey, writeStack(itemEntry.getValue()));
                    tableChanged = true;
                    added++;
                }
            }
            if (tableChanged) {
                observedTables.add(tableKey, observed);
                DISCOVERY_CACHE.remove(tableEntry.getKey());
            }
        }
        return added;
    }

    private static void applyRules(ResourceLocation table, List<ItemStack> base,
                                   ObjectArrayList<ItemStack> modified, LootContext context) {
        JsonElement tableRules = LootEditorConfig.extraRules().get(table.toString());
        if (tableRules == null || !tableRules.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> ruleEntry : tableRules.getAsJsonObject().entrySet()) {
            ResourceLocation originalId = ResourceLocation.tryParse(ruleEntry.getKey());
            if (originalId == null || !ruleEntry.getValue().isJsonObject()) {
                continue;
            }
            int preserve = base.stream()
                    .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(originalId))
                    .mapToInt(ItemStack::getCount).sum();
            for (int index = 0; index < modified.size(); ) {
                ItemStack stack = modified.get(index);
                if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(originalId)) {
                    index++;
                    continue;
                }
                if (preserve >= stack.getCount()) {
                    preserve -= stack.getCount();
                    index++;
                } else if (preserve > 0) {
                    stack.setCount(preserve);
                    preserve = 0;
                    index++;
                } else {
                    modified.remove(index);
                }
            }

            JsonObject rule = ruleEntry.getValue().getAsJsonObject();
            int chance = Math.max(0, Math.min(10_000, rule.has("chance") ? rule.get("chance").getAsInt() : 0));
            ItemStack replacement = readStack(rule);
            if (!replacement.isEmpty() && context.getRandom().nextInt(10_000) < chance) {
                modified.add(replacement.copy());
            }
        }
    }

    private static Map<ResourceLocation, Integer> counts(List<ItemStack> stacks) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (ItemStack stack : stacks) {
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static JsonObject writeStack(ItemStack stack) {
        JsonObject object = new JsonObject();
        object.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        object.addProperty("count", Math.max(1, stack.getCount()));
        if (stack.hasTag()) {
            object.addProperty("tag", stack.getTag().toString());
        }
        return object;
    }

    private static ItemStack readStack(JsonObject object) {
        if (!object.has("item")) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(object.get("item").getAsString());
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, object.has("count") ? Math.max(1, object.get("count").getAsInt()) : 1);
        if (object.has("tag")) {
            try {
                stack.setTag(TagParser.parseTag(object.get("tag").getAsString()));
            } catch (Exception ignored) {
            }
        }
        return stack;
    }
}
