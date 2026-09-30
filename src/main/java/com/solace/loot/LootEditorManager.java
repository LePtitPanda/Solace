package com.solace.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.ForgeHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LootEditorManager {
    public static final int PAGE_SIZE = 27;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, LootTable> CACHE = new HashMap<>();

    private LootEditorManager() {
    }

    /** Called by the LootDataManager mixin. Null means that vanilla should return its normal element. */
    public static synchronized LootTable resolve(ResourceLocation requested, Map<LootDataId<?>, ?> rawElements) {
        JsonObject overrides = LootEditorConfig.overrides();
        JsonObject replacements = LootEditorConfig.replacements();
        if (!overrides.has(requested.toString()) && !replacements.has(requested.toString())) {
            return null;
        }
        LootTable cached = CACHE.get(requested);
        if (cached != null) {
            return cached;
        }

        ResourceLocation current = requested;
        Set<ResourceLocation> visited = new HashSet<>();
        for (int depth = 0; depth < 32 && visited.add(current); depth++) {
            JsonElement override = overrides.get(current.toString());
            if (override != null && override.isJsonObject()) {
                try {
                    // Forge's LootPool deserializer requires the context installed by this helper.
                    // Calling Gson directly works only during a data-pack reload and otherwise falls
                    // back to the old table after ForgeHooks.readPoolName throws.
                    LootTable table = ForgeHooks.loadLootTable(
                            LootDataType.TABLE.parser(), requested, override, true);
                    if (table != null) {
                        CACHE.put(requested, table);
                        return table;
                    }
                } catch (Exception exception) {
                    LOGGER.error("[Solace] Invalid edited loot table {}", current, exception);
                    return null;
                }
            }

            JsonElement replacement = replacements.get(current.toString());
            if (replacement == null || !replacement.isJsonPrimitive()) {
                break;
            }
            ResourceLocation next = ResourceLocation.tryParse(replacement.getAsString());
            if (next == null) {
                break;
            }
            current = next;
        }

        Object raw = rawElements.get(new LootDataId<>(LootDataType.TABLE, current));
        if (raw instanceof LootTable table) {
            if (current.equals(requested)) {
                CACHE.put(requested, table);
                return table;
            }
            try {
                JsonElement json = LootDataType.TABLE.parser().toJsonTree(table, LootTable.class);
                LootTable replacement = ForgeHooks.loadLootTable(
                        LootDataType.TABLE.parser(), requested, json, true);
                if (replacement != null) {
                    CACHE.put(requested, replacement);
                    return replacement;
                }
            } catch (Exception exception) {
                LOGGER.error("[Solace] Could not copy replacement loot table {} as {}", current, requested, exception);
            }
        }
        return null;
    }

    public static synchronized void clearCache() {
        CACHE.clear();
    }

    public static LootEditorSnapshot snapshot(MinecraftServer server, net.minecraft.core.BlockPos pos,
                                              ResourceLocation source, int requestedPool, int requestedPage) {
        LootDataManager manager = server.getLootData();
        LootTable effectiveTable = manager.getLootTable(source);
        ResourceLocation effective = effectiveId(source);
        JsonObject json = tableJson(source, effectiveTable);
        PoolView poolView = collectPools(json);
        List<LootEditorEntry> extras = new ArrayList<>();
        for (LootExtraManager.Extra extra : LootExtraManager.discover(source)) {
            int count = Math.max(1, extra.stack().getCount());
            ItemStack shown = extra.stack().copy();
            shown.setCount(1);
            extras.add(new LootEditorEntry("@extra/" + extra.originalItem(), shown, -1,
                    1, 1, extra.chanceBasisPoints(), 0, count, count,
                    true, 0, true, true, extra.originalItem().toString()));
        }
        int externalCount = extras.size();
        int viewCount = poolView.pools().size() + (externalCount > 0 ? 1 : 0);
        int selectedPool = viewCount == 0 ? 0 : Math.max(0, Math.min(requestedPool, viewCount - 1));
        List<LootEditorEntry> selectedEntries = selectedPool < poolView.entries().size()
                ? poolView.entries().get(selectedPool) : extras;
        int pageCount = Math.max(1, (selectedEntries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = Math.min(page * PAGE_SIZE, selectedEntries.size());
        int to = Math.min(from + PAGE_SIZE, selectedEntries.size());
        List<ResourceLocation> structures = LootStructureIndex.structuresFor(source);
        if (!effective.equals(source)) {
            Set<ResourceLocation> merged = new java.util.LinkedHashSet<>(structures);
            merged.addAll(LootStructureIndex.structuresFor(effective));
            structures = List.copyOf(merged);
        }
        return new LootEditorSnapshot(pos, source, effective, selectedPool, poolView.pools(),
                page, pageCount, selectedEntries.size(), poolView.totalEntries(), externalCount,
                List.copyOf(selectedEntries.subList(from, to)), structures, LootStructureIndex.isReady(),
                isCustomized(source), suppressesModifiers(source));
    }

    private static synchronized boolean isCustomized(ResourceLocation source) {
        String key = source.toString();
        return LootEditorConfig.overrides().has(key)
                || LootEditorConfig.replacements().has(key)
                || LootEditorConfig.extraRules().has(key);
    }

    public static boolean setReplacement(MinecraftServer server, ResourceLocation source, ResourceLocation target) {
        if (!server.getLootData().getKeys(LootDataType.TABLE).contains(target)) {
            return false;
        }
        synchronized (LootEditorManager.class) {
            enableAuthoritativeModeForFirstEdit(source);
            LootEditorConfig.overrides().remove(source.toString());
            if (source.equals(target)) {
                LootEditorConfig.replacements().remove(source.toString());
            } else {
                LootEditorConfig.replacements().addProperty(source.toString(), target.toString());
            }
            clearCache();
            LootEditorConfig.save();
        }
        LootEditorAliCompat.requestRefresh(server);
        return true;
    }

    public static synchronized void reset(MinecraftServer server, ResourceLocation source) {
        LootEditorConfig.overrides().remove(source.toString());
        LootEditorConfig.replacements().remove(source.toString());
        LootEditorConfig.suppressModifiers().remove(source.toString());
        LootEditorConfig.extraRules().remove(source.toString());
        LootExtraManager.clearDiscoveryCache(source);
        clearCache();
        LootEditorConfig.save();
        LootEditorAliCompat.requestRefresh(server);
    }

    public static synchronized boolean suppressesModifiers(ResourceLocation source) {
        JsonElement value = LootEditorConfig.suppressModifiers().get(source.toString());
        if (value != null && value.isJsonPrimitive()) {
            return value.getAsBoolean();
        }
        // Configs written by the first editor build have no flag. Treat their existing
        // edits as authoritative too, while untouched tables keep normal Forge behavior.
        String key = source.toString();
        return LootEditorConfig.overrides().has(key) || LootEditorConfig.replacements().has(key);
    }

    public static synchronized void toggleModifiers(MinecraftServer server, ResourceLocation source) {
        LootEditorConfig.suppressModifiers().addProperty(source.toString(), !suppressesModifiers(source));
        clearCache();
        LootEditorConfig.save();
        LootEditorAliCompat.requestRefresh(server);
    }

    public static synchronized boolean setWeight(MinecraftServer server, ResourceLocation source, String path, int weight) {
        JsonObject json = ensureOverride(server, source);
        JsonObject entry = objectAt(json, path);
        if (entry == null || !isItemEntry(entry)) {
            return false;
        }
        int value = Math.max(1, Math.min(100_000, weight));
        if (value == 1) {
            entry.remove("weight");
        } else {
            entry.addProperty("weight", value);
        }
        persist(server, source, json);
        return true;
    }

    public static synchronized boolean setEntrySettings(MinecraftServer server, ResourceLocation source, String path,
                                                         int weight, int chanceBasisPoints,
                                                         int countMin, int countMax, boolean editCount) {
        JsonObject json = ensureOverride(server, source);
        JsonObject entry = objectAt(json, path);
        if (entry == null || !isItemEntry(entry)) {
            return false;
        }

        int safeWeight = Math.max(1, Math.min(100_000, weight));
        if (safeWeight == 1) {
            entry.remove("weight");
        } else {
            entry.addProperty("weight", safeWeight);
        }
        setRandomChance(entry, Math.max(0, Math.min(10_000, chanceBasisPoints)));
        if (editCount) {
            setCount(entry, Math.max(1, Math.min(4096, countMin)),
                    Math.max(1, Math.min(4096, countMax)));
        }
        persist(server, source, json);
        return true;
    }

    public static synchronized boolean setExtraChance(MinecraftServer server, ResourceLocation source,
                                                       ResourceLocation originalItem, ItemStack shownStack,
                                                       int chanceBasisPoints) {
        LootExtraManager.setRule(source, originalItem, shownStack, chanceBasisPoints);
        LootEditorConfig.suppressModifiers().addProperty(source.toString(), false);
        LootEditorConfig.save();
        LootEditorAliCompat.requestRefresh(server);
        return true;
    }

    public static synchronized boolean setExtraSettings(MinecraftServer server, ResourceLocation source,
                                                         ResourceLocation originalItem, ItemStack shownStack,
                                                         int chanceBasisPoints, int count) {
        ItemStack updated = shownStack.copy();
        updated.setCount(Math.max(1, Math.min(4096, count)));
        LootExtraManager.setRule(source, originalItem, updated,
                Math.max(0, Math.min(10_000, chanceBasisPoints)));
        LootEditorConfig.suppressModifiers().addProperty(source.toString(), false);
        LootEditorConfig.save();
        LootEditorAliCompat.requestRefresh(server);
        return true;
    }

    public static synchronized boolean replaceExtra(MinecraftServer server, ResourceLocation source,
                                                     ResourceLocation originalItem, ItemStack replacement,
                                                     int chanceBasisPoints) {
        if (replacement.isEmpty()) {
            return false;
        }
        LootExtraManager.setRule(source, originalItem, replacement, Math.max(0, chanceBasisPoints));
        LootEditorConfig.suppressModifiers().addProperty(source.toString(), false);
        LootEditorConfig.save();
        LootEditorAliCompat.requestRefresh(server);
        return true;
    }

    public static synchronized boolean replaceItem(MinecraftServer server, ResourceLocation source,
                                                   String path, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        JsonObject json = ensureOverride(server, source);
        JsonObject entry = objectAt(json, path);
        if (entry == null || !isItemEntry(entry)) {
            return false;
        }
        // Replacing an item must not erase its pool membership, conditions, count range,
        // enchantments, or other advanced functions. Those settings belong to the entry.
        entry.addProperty("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (stack.hasTag()) {
            setNbt(entry, stack.getTag().toString());
        }
        persist(server, source, json);
        return true;
    }

    public static synchronized boolean addItem(MinecraftServer server, ResourceLocation source,
                                               int poolIndex, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        JsonObject json = ensureOverride(server, source);
        JsonArray pools = pools(json);
        if (pools.isEmpty()) {
            poolIndex = addPool(json, ItemStack.EMPTY);
        }
        if (poolIndex < 0 || poolIndex >= pools.size() || !pools.get(poolIndex).isJsonObject()) {
            return false;
        }
        JsonObject pool = pools.get(poolIndex).getAsJsonObject();
        JsonArray entries;
        if (!pool.has("entries") || !pool.get("entries").isJsonArray()) {
            entries = new JsonArray();
            pool.add("entries", entries);
        } else {
            entries = pool.getAsJsonArray("entries");
        }
        entries.add(itemEntry(stack, 1));
        persist(server, source, json);
        return true;
    }

    public static synchronized int addPool(MinecraftServer server, ResourceLocation source, ItemStack firstItem) {
        JsonObject json = ensureOverride(server, source);
        int index = addPool(json, firstItem);
        persist(server, source, json);
        return index;
    }

    public static synchronized boolean removePool(MinecraftServer server, ResourceLocation source, int poolIndex) {
        JsonObject json = ensureOverride(server, source);
        JsonArray pools = pools(json);
        if (poolIndex < 0 || poolIndex >= pools.size()) {
            return false;
        }
        pools.remove(poolIndex);
        persist(server, source, json);
        return true;
    }

    public static synchronized boolean setPoolSettings(MinecraftServer server, ResourceLocation source, int poolIndex,
                                                        double rollsMin, double rollsMax,
                                                        double bonusMin, double bonusMax) {
        JsonObject json = ensureOverride(server, source);
        JsonArray pools = pools(json);
        if (poolIndex < 0 || poolIndex >= pools.size() || !pools.get(poolIndex).isJsonObject()) {
            return false;
        }
        JsonObject pool = pools.get(poolIndex).getAsJsonObject();
        NumberRange rolls = numberRange(pool.get("rolls"), 1.0D);
        NumberRange bonus = numberRange(pool.get("bonus_rolls"), 0.0D);
        if (rolls.editable()) {
            pool.add("rolls", numberProvider(rollsMin, rollsMax));
        }
        if (bonus.editable()) {
            pool.add("bonus_rolls", numberProvider(bonusMin, bonusMax));
        }
        persist(server, source, json);
        return true;
    }

    public static synchronized boolean removeItem(MinecraftServer server, ResourceLocation source, String path) {
        JsonObject json = ensureOverride(server, source);
        String[] tokens = tokens(path);
        if (tokens.length < 2) {
            return false;
        }
        JsonElement parent = walk(json, tokens, tokens.length - 1);
        if (!(parent instanceof JsonArray array)) {
            return false;
        }
        int index = parseIndex(tokens[tokens.length - 1]);
        if (index < 0 || index >= array.size() || !array.get(index).isJsonObject()
                || !isItemEntry(array.get(index).getAsJsonObject())) {
            return false;
        }
        array.remove(index);
        persist(server, source, json);
        return true;
    }

    private static JsonObject ensureOverride(MinecraftServer server, ResourceLocation source) {
        JsonElement existing = LootEditorConfig.overrides().get(source.toString());
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject().deepCopy();
        }
        LootTable effective = server.getLootData().getLootTable(source);
        JsonObject json = LootDataType.TABLE.parser().toJsonTree(effective, LootTable.class).getAsJsonObject();
        LootEditorConfig.replacements().remove(source.toString());
        return json;
    }

    private static void persist(MinecraftServer server, ResourceLocation source, JsonObject json) {
        enableAuthoritativeModeForFirstEdit(source);
        LootEditorConfig.replacements().remove(source.toString());
        LootEditorConfig.overrides().add(source.toString(), json);
        clearCache();
        LootEditorConfig.save();
        LootEditorAliCompat.requestRefresh(server);
    }

    private static void enableAuthoritativeModeForFirstEdit(ResourceLocation source) {
        String key = source.toString();
        boolean alreadyEdited = LootEditorConfig.overrides().has(key)
                || LootEditorConfig.replacements().has(key);
        if (!alreadyEdited && !LootEditorConfig.suppressModifiers().has(key)) {
            LootEditorConfig.suppressModifiers().addProperty(key, true);
        }
    }

    private static JsonObject tableJson(ResourceLocation source, LootTable effective) {
        JsonElement override = LootEditorConfig.overrides().get(source.toString());
        if (override != null && override.isJsonObject()) {
            return override.getAsJsonObject().deepCopy();
        }
        return LootDataType.TABLE.parser().toJsonTree(effective, LootTable.class).getAsJsonObject();
    }

    private static ResourceLocation effectiveId(ResourceLocation source) {
        ResourceLocation current = source;
        Set<ResourceLocation> visited = new HashSet<>();
        JsonObject replacements = LootEditorConfig.replacements();
        while (visited.add(current)) {
            JsonElement nextValue = replacements.get(current.toString());
            if (nextValue == null || !nextValue.isJsonPrimitive()) {
                return current;
            }
            ResourceLocation next = ResourceLocation.tryParse(nextValue.getAsString());
            if (next == null) {
                return current;
            }
            current = next;
        }
        return source;
    }

    private static PoolView collectPools(JsonObject table) {
        List<LootEditorPool> poolInfo = new ArrayList<>();
        List<List<LootEditorEntry>> entriesByPool = new ArrayList<>();
        int totalEntries = 0;
        JsonArray pools = table.has("pools") && table.get("pools").isJsonArray()
                ? table.getAsJsonArray("pools") : new JsonArray();
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            if (!pools.get(poolIndex).isJsonObject()) {
                continue;
            }
            JsonObject pool = pools.get(poolIndex).getAsJsonObject();
            JsonArray poolEntries = pool.has("entries") && pool.get("entries").isJsonArray()
                    ? pool.getAsJsonArray("entries") : new JsonArray();
            int poolWeight = simplePoolWeight(poolEntries);
            List<LootEditorEntry> entries = new ArrayList<>();
            collectEntries(poolEntries, "/pools/" + poolIndex + "/entries", poolIndex,
                    poolWeight, true, entries);
            NumberRange rolls = numberRange(pool.get("rolls"), 1.0D);
            NumberRange bonus = numberRange(pool.get("bonus_rolls"), 0.0D);
            String name = pool.has("name") && pool.get("name").isJsonPrimitive()
                    ? pool.get("name").getAsString() : "Pool " + (poolIndex + 1);
            int conditionCount = pool.has("conditions") && pool.get("conditions").isJsonArray()
                    ? pool.getAsJsonArray("conditions").size() : 0;
            int otherEntries = Math.max(0, poolEntries.size() - countDirectItems(poolEntries));
            poolInfo.add(new LootEditorPool(poolIndex, name,
                    rolls.min(), rolls.max(), rolls.editable(),
                    bonus.min(), bonus.max(), bonus.editable(),
                    entries.size(), otherEntries, conditionCount));
            entriesByPool.add(List.copyOf(entries));
            totalEntries += entries.size();
        }
        return new PoolView(List.copyOf(poolInfo), List.copyOf(entriesByPool), totalEntries);
    }

    private static void collectEntries(JsonArray array, String path, int poolIndex, int poolWeight,
                                       boolean direct, List<LootEditorEntry> output) {
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                continue;
            }
            JsonObject object = array.get(i).getAsJsonObject();
            String childPath = path + "/" + i;
            if (isItemEntry(object)) {
                ItemStack stack = previewStack(object);
                if (!stack.isEmpty()) {
                    EntrySettings settings = entrySettings(object);
                    stack.setCount(1);
                    output.add(new LootEditorEntry(childPath, stack, poolIndex,
                            Math.max(1, object.has("weight") ? object.get("weight").getAsInt() : 1),
                            direct ? poolWeight : 0, settings.chanceBasisPoints(), settings.otherConditions(),
                            settings.count().minInt(), settings.count().maxInt(), settings.count().editable(),
                            settings.otherFunctions(), direct, false, ""));
                }
            }
            if (object.has("children") && object.get("children").isJsonArray()) {
                collectEntries(object.getAsJsonArray("children"), childPath + "/children", poolIndex,
                        0, false, output);
            }
        }
    }

    private static int simplePoolWeight(JsonArray array) {
        int sum = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                return 0;
            }
            JsonObject object = element.getAsJsonObject();
            if (object.has("children") && object.get("children").isJsonArray()) {
                return 0;
            }
            if (object.has("quality") && object.get("quality").isJsonPrimitive()
                    && object.get("quality").getAsInt() != 0) {
                return 0;
            }
            sum += Math.max(1, object.has("weight") ? object.get("weight").getAsInt() : 1);
        }
        return Math.max(1, sum);
    }

    private static int countDirectItems(JsonArray array) {
        int count = 0;
        for (JsonElement element : array) {
            if (element.isJsonObject() && isItemEntry(element.getAsJsonObject())) {
                count++;
            }
        }
        return count;
    }

    private static boolean isItemEntry(JsonObject entry) {
        if (!entry.has("type") || !entry.get("type").isJsonPrimitive()) {
            return false;
        }
        String type = entry.get("type").getAsString();
        return type.equals("item") || type.equals("minecraft:item");
    }

    private static ItemStack previewStack(JsonObject entry) {
        if (!entry.has("name")) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.get("name").getAsString());
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (entry.has("functions") && entry.get("functions").isJsonArray()) {
            for (JsonElement functionElement : entry.getAsJsonArray("functions")) {
                if (!functionElement.isJsonObject()) {
                    continue;
                }
                JsonObject function = functionElement.getAsJsonObject();
                String type = function.has("function") ? function.get("function").getAsString() : "";
                try {
                    if ((type.equals("minecraft:set_count") || type.equals("set_count")) && function.has("count")
                            && function.get("count").isJsonPrimitive()) {
                        stack.setCount(Math.max(1, function.get("count").getAsInt()));
                    } else if ((type.equals("minecraft:set_nbt") || type.equals("set_nbt")) && function.has("tag")) {
                        stack.setTag(TagParser.parseTag(function.get("tag").getAsString()));
                    }
                } catch (Exception ignored) {
                    // Preview only; the original function remains untouched in the table JSON.
                }
            }
        }
        EntrySettings settings = entrySettings(entry);
        if (settings.count().editable() && settings.count().minInt() == settings.count().maxInt()) {
            stack.setCount(Math.min(stack.getMaxStackSize(), settings.count().minInt()));
        }
        return stack;
    }

    private static JsonObject itemEntry(ItemStack source, int weight) {
        ItemStack stack = source.copy();
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (weight != 1) {
            entry.addProperty("weight", weight);
        }
        JsonArray functions = new JsonArray();
        if (stack.getCount() != 1) {
            JsonObject count = new JsonObject();
            count.addProperty("function", "minecraft:set_count");
            count.addProperty("count", stack.getCount());
            functions.add(count);
        }
        if (stack.hasTag()) {
            JsonObject nbt = new JsonObject();
            nbt.addProperty("function", "minecraft:set_nbt");
            nbt.addProperty("tag", stack.getTag().toString());
            functions.add(nbt);
        }
        if (!functions.isEmpty()) {
            entry.add("functions", functions);
        }
        return entry;
    }

    private static EntrySettings entrySettings(JsonObject entry) {
        int chance = 10_000;
        int otherConditions = 0;
        boolean foundChance = false;
        if (entry.has("conditions") && entry.get("conditions").isJsonArray()) {
            for (JsonElement value : entry.getAsJsonArray("conditions")) {
                if (!value.isJsonObject()) {
                    otherConditions++;
                    continue;
                }
                JsonObject condition = value.getAsJsonObject();
                String type = type(condition, "condition");
                if (!foundChance && (type.equals("random_chance") || type.equals("minecraft:random_chance"))
                        && condition.has("chance") && condition.get("chance").isJsonPrimitive()) {
                    chance = Math.max(0, Math.min(10_000,
                            (int) Math.round(condition.get("chance").getAsDouble() * 10_000.0D)));
                    foundChance = true;
                } else {
                    otherConditions++;
                }
            }
        }

        NumberRange count = new NumberRange(1.0D, 1.0D, true);
        int otherFunctions = 0;
        boolean foundCount = false;
        if (entry.has("functions") && entry.get("functions").isJsonArray()) {
            for (JsonElement value : entry.getAsJsonArray("functions")) {
                if (!value.isJsonObject()) {
                    otherFunctions++;
                    continue;
                }
                JsonObject function = value.getAsJsonObject();
                String functionType = type(function, "function");
                if (!foundCount && (functionType.equals("set_count") || functionType.equals("minecraft:set_count"))
                        && function.has("count")) {
                    count = numberRange(function.get("count"), 1.0D);
                    foundCount = true;
                } else {
                    otherFunctions++;
                }
            }
        }
        return new EntrySettings(chance, otherConditions, count, otherFunctions);
    }

    private static void setRandomChance(JsonObject entry, int chanceBasisPoints) {
        JsonArray conditions = entry.has("conditions") && entry.get("conditions").isJsonArray()
                ? entry.getAsJsonArray("conditions") : new JsonArray();
        int existing = -1;
        for (int i = 0; i < conditions.size(); i++) {
            if (!conditions.get(i).isJsonObject()) {
                continue;
            }
            String conditionType = type(conditions.get(i).getAsJsonObject(), "condition");
            if (conditionType.equals("random_chance") || conditionType.equals("minecraft:random_chance")) {
                existing = i;
                break;
            }
        }
        if (chanceBasisPoints >= 10_000) {
            if (existing >= 0) {
                conditions.remove(existing);
            }
        } else {
            JsonObject condition = existing >= 0 ? conditions.get(existing).getAsJsonObject() : new JsonObject();
            condition.addProperty("condition", "minecraft:random_chance");
            condition.addProperty("chance", chanceBasisPoints / 10_000.0D);
            if (existing < 0) {
                conditions.add(condition);
            }
        }
        if (conditions.isEmpty()) {
            entry.remove("conditions");
        } else {
            entry.add("conditions", conditions);
        }
    }

    private static void setCount(JsonObject entry, int requestedMin, int requestedMax) {
        int min = Math.min(requestedMin, requestedMax);
        int max = Math.max(requestedMin, requestedMax);
        JsonArray functions = entry.has("functions") && entry.get("functions").isJsonArray()
                ? entry.getAsJsonArray("functions") : new JsonArray();
        JsonObject countFunction = null;
        for (JsonElement value : functions) {
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject function = value.getAsJsonObject();
            String functionType = type(function, "function");
            if (functionType.equals("set_count") || functionType.equals("minecraft:set_count")) {
                if (countFunction != null || !numberRange(function.get("count"), 1.0D).editable()) {
                    return;
                }
                countFunction = function;
            }
        }
        if (countFunction == null) {
            if (min == 1 && max == 1) {
                return;
            }
            countFunction = new JsonObject();
            countFunction.addProperty("function", "minecraft:set_count");
            functions.add(countFunction);
        }
        countFunction.add("count", numberProvider(min, max));
        entry.add("functions", functions);
    }

    private static void setNbt(JsonObject entry, String tag) {
        JsonArray functions = entry.has("functions") && entry.get("functions").isJsonArray()
                ? entry.getAsJsonArray("functions") : new JsonArray();
        JsonObject nbt = null;
        for (JsonElement value : functions) {
            if (value.isJsonObject()) {
                String functionType = type(value.getAsJsonObject(), "function");
                if (functionType.equals("set_nbt") || functionType.equals("minecraft:set_nbt")) {
                    nbt = value.getAsJsonObject();
                    break;
                }
            }
        }
        if (nbt == null) {
            nbt = new JsonObject();
            nbt.addProperty("function", "minecraft:set_nbt");
            functions.add(nbt);
        }
        nbt.addProperty("tag", tag);
        entry.add("functions", functions);
    }

    private static String type(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static NumberRange numberRange(JsonElement element, double fallback) {
        if (element == null) {
            return new NumberRange(fallback, fallback, true);
        }
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                double value = element.getAsDouble();
                return new NumberRange(value, value, true);
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                String providerType = type(object, "type");
                if ((providerType.equals("uniform") || providerType.equals("minecraft:uniform"))
                        && object.has("min") && object.get("min").isJsonPrimitive()
                        && object.has("max") && object.get("max").isJsonPrimitive()) {
                    return new NumberRange(object.get("min").getAsDouble(), object.get("max").getAsDouble(), true);
                }
                if ((providerType.equals("constant") || providerType.equals("minecraft:constant"))
                        && object.has("value") && object.get("value").isJsonPrimitive()) {
                    double value = object.get("value").getAsDouble();
                    return new NumberRange(value, value, true);
                }
            }
        } catch (RuntimeException ignored) {
            // Preserve providers we cannot safely represent in the simple editor.
        }
        return new NumberRange(fallback, fallback, false);
    }

    private static JsonElement numberProvider(double requestedMin, double requestedMax) {
        double min = Math.max(0.0D, Math.min(4096.0D, Math.min(requestedMin, requestedMax)));
        double max = Math.max(min, Math.min(4096.0D, Math.max(requestedMin, requestedMax)));
        if (Math.abs(min - max) < 0.000001D) {
            return new com.google.gson.JsonPrimitive(min);
        }
        JsonObject uniform = new JsonObject();
        uniform.addProperty("type", "minecraft:uniform");
        uniform.addProperty("min", min);
        uniform.addProperty("max", max);
        return uniform;
    }

    private static JsonArray pools(JsonObject table) {
        if (table.has("pools") && table.get("pools").isJsonArray()) {
            return table.getAsJsonArray("pools");
        }
        JsonArray pools = new JsonArray();
        table.add("pools", pools);
        return pools;
    }

    private static int addPool(JsonObject table, ItemStack firstItem) {
        JsonArray pools = pools(table);
        JsonObject pool = new JsonObject();
        pool.addProperty("name", uniquePoolName(pools));
        pool.addProperty("rolls", 1.0D);
        pool.addProperty("bonus_rolls", 0.0D);
        JsonArray entries = new JsonArray();
        if (firstItem != null && !firstItem.isEmpty()) {
            entries.add(itemEntry(firstItem, 1));
        }
        pool.add("entries", entries);
        pools.add(pool);
        return pools.size() - 1;
    }

    private static String uniquePoolName(JsonArray pools) {
        Set<String> names = new HashSet<>();
        for (JsonElement value : pools) {
            if (value.isJsonObject() && value.getAsJsonObject().has("name")) {
                names.add(value.getAsJsonObject().get("name").getAsString());
            }
        }
        for (int index = 1; ; index++) {
            String candidate = "solace_pool_" + index;
            if (!names.contains(candidate)) {
                return candidate;
            }
        }
    }

    private static JsonObject objectAt(JsonObject root, String path) {
        JsonElement element = walk(root, tokens(path), tokens(path).length);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonElement walk(JsonElement root, String[] tokens, int length) {
        JsonElement current = root;
        for (int i = 0; i < length; i++) {
            if (current.isJsonObject()) {
                current = current.getAsJsonObject().get(tokens[i]);
            } else if (current.isJsonArray()) {
                int index = parseIndex(tokens[i]);
                JsonArray array = current.getAsJsonArray();
                current = index >= 0 && index < array.size() ? array.get(index) : null;
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String[] tokens(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .filter(token -> !token.isEmpty()).toArray(String[]::new);
    }

    private static int parseIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private record PoolView(List<LootEditorPool> pools, List<List<LootEditorEntry>> entries, int totalEntries) {
    }

    private record EntrySettings(int chanceBasisPoints, int otherConditions,
                                 NumberRange count, int otherFunctions) {
    }

    private record NumberRange(double min, double max, boolean editable) {
        private int minInt() {
            return Math.max(1, (int) Math.floor(min));
        }

        private int maxInt() {
            return Math.max(minInt(), (int) Math.floor(max));
        }
    }

}
