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
                                              ResourceLocation source, int requestedPage) {
        LootDataManager manager = server.getLootData();
        LootTable effectiveTable = manager.getLootTable(source);
        ResourceLocation effective = effectiveId(source);
        JsonObject json = tableJson(source, effectiveTable);
        List<LootEditorEntry> direct = collectEntries(json);
        List<LootEditorEntry> all = new ArrayList<>(direct);
        for (LootExtraManager.Extra extra : LootExtraManager.discover(source)) {
            all.add(new LootEditorEntry("@extra/" + extra.originalItem(), extra.stack(), extra.chanceBasisPoints(),
                    10_000, true, extra.originalItem().toString()));
        }
        int externalCount = all.size() - direct.size();
        int pageCount = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = Math.min(page * PAGE_SIZE, all.size());
        int to = Math.min(from + PAGE_SIZE, all.size());
        List<ResourceLocation> structures = LootStructureIndex.structuresFor(source);
        if (!effective.equals(source)) {
            Set<ResourceLocation> merged = new java.util.LinkedHashSet<>(structures);
            merged.addAll(LootStructureIndex.structuresFor(effective));
            structures = List.copyOf(merged);
        }
        return new LootEditorSnapshot(pos, source, effective, page, pageCount, all.size(),
                direct.size(), externalCount,
                List.copyOf(all.subList(from, to)), structures, LootStructureIndex.isReady(),
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

    public static synchronized boolean setExtraChance(MinecraftServer server, ResourceLocation source,
                                                       ResourceLocation originalItem, ItemStack shownStack,
                                                       int chanceBasisPoints) {
        LootExtraManager.setRule(source, originalItem, shownStack, chanceBasisPoints);
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
        int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
        JsonObject replacement = itemEntry(stack, weight);
        entry.entrySet().clear();
        replacement.entrySet().forEach(value -> entry.add(value.getKey(), value.getValue()));
        persist(server, source, json);
        return true;
    }

    public static synchronized boolean addItem(MinecraftServer server, ResourceLocation source, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        JsonObject json = ensureOverride(server, source);
        JsonArray pools;
        if (!json.has("pools") || !json.get("pools").isJsonArray()) {
            pools = new JsonArray();
            json.add("pools", pools);
        } else {
            pools = json.getAsJsonArray("pools");
        }
        JsonObject pool;
        if (pools.isEmpty()) {
            pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", new JsonArray());
            pools.add(pool);
        } else {
            pool = pools.get(0).getAsJsonObject();
        }
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

    private static List<LootEditorEntry> collectEntries(JsonObject table) {
        List<LootEditorEntry> entries = new ArrayList<>();
        JsonArray pools = table.has("pools") && table.get("pools").isJsonArray()
                ? table.getAsJsonArray("pools") : new JsonArray();
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            if (!pools.get(poolIndex).isJsonObject()) {
                continue;
            }
            JsonObject pool = pools.get(poolIndex).getAsJsonObject();
            JsonArray poolEntries = pool.has("entries") && pool.get("entries").isJsonArray()
                    ? pool.getAsJsonArray("entries") : new JsonArray();
            int totalWeight = directWeight(poolEntries);
            collectEntries(poolEntries, "/pools/" + poolIndex + "/entries", Math.max(1, totalWeight), entries);
        }
        return entries;
    }

    private static void collectEntries(JsonArray array, String path, int poolWeight, List<LootEditorEntry> output) {
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                continue;
            }
            JsonObject object = array.get(i).getAsJsonObject();
            String childPath = path + "/" + i;
            if (isItemEntry(object)) {
                ItemStack stack = previewStack(object);
                if (!stack.isEmpty()) {
                    output.add(new LootEditorEntry(childPath, stack,
                            Math.max(1, object.has("weight") ? object.get("weight").getAsInt() : 1), poolWeight,
                            false, ""));
                }
            }
            if (object.has("children") && object.get("children").isJsonArray()) {
                collectEntries(object.getAsJsonArray("children"), childPath + "/children", poolWeight, output);
            }
        }
    }

    private static int directWeight(JsonArray array) {
        int sum = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (isItemEntry(object)) {
                sum += Math.max(1, object.has("weight") ? object.get("weight").getAsInt() : 1);
            }
            if (object.has("children") && object.get("children").isJsonArray()) {
                sum += directWeight(object.getAsJsonArray("children"));
            }
        }
        return sum;
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

}
