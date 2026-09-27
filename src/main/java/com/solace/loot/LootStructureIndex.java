package com.solace.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Lazy one-time scan of structure template NBT, cached outside world saves. */
public final class LootStructureIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("solace-loot-structure-index.json");
    private static volatile Map<ResourceLocation, List<ResourceLocation>> index = load();
    private static volatile boolean ready = index != null;
    private static volatile boolean building;

    private LootStructureIndex() {
    }

    public static boolean isReady() {
        return ready;
    }

    public static List<ResourceLocation> structuresFor(ResourceLocation table) {
        Map<ResourceLocation, List<ResourceLocation>> current = index;
        return current == null ? List.of() : current.getOrDefault(table, List.of());
    }

    public static void ensureBuilding(MinecraftServer server, ServerPlayer requester) {
        if (ready || building) {
            return;
        }
        synchronized (LootStructureIndex.class) {
            if (ready || building) {
                return;
            }
            building = true;
        }

        Map<ResourceLocation, Resource> resources = server.getResourceManager().listResources(
                "structures", id -> id.getPath().endsWith(".nbt"));
        requester.sendSystemMessage(Component.literal("Solace is indexing " + resources.size()
                + " structure templates once in the background. The result will be cached."));

        CompletableFuture.supplyAsync(() -> build(resources)).whenComplete((built, failure) -> server.execute(() -> {
            building = false;
            if (failure != null) {
                LOGGER.error("[Solace] Structure loot index failed", failure);
                requester.sendSystemMessage(Component.literal("Loot structure indexing failed; see the log."));
                return;
            }
            index = built;
            ready = true;
            save(built);
            int associations = built.values().stream().mapToInt(List::size).sum();
            requester.sendSystemMessage(Component.literal("Loot structure index ready: " + built.size()
                    + " loot tables across " + associations + " template associations. Reopen the editor to view it."));
        }));
    }

    public static void rebuild(MinecraftServer server, ServerPlayer requester) {
        ready = false;
        index = null;
        ensureBuilding(server, requester);
    }

    private static Map<ResourceLocation, List<ResourceLocation>> build(Map<ResourceLocation, Resource> resources) {
        Map<ResourceLocation, Set<ResourceLocation>> mutable = new HashMap<>();
        resources.forEach((resourceId, resource) -> {
            ResourceLocation structure = structureId(resourceId);
            if (structure == null) {
                return;
            }
            try (InputStream stream = resource.open()) {
                CompoundTag root = NbtIo.readCompressed(stream);
                Set<ResourceLocation> tables = new LinkedHashSet<>();
                findLootTables(root, tables);
                tables.forEach(table -> mutable.computeIfAbsent(table, ignored -> new LinkedHashSet<>()).add(structure));
            } catch (Exception exception) {
                LOGGER.warn("[Solace] Could not inspect structure template {}", resourceId, exception);
            }
        });
        Map<ResourceLocation, List<ResourceLocation>> result = new HashMap<>();
        mutable.forEach((table, structures) -> result.put(table, structures.stream().sorted().toList()));
        return Map.copyOf(result);
    }

    private static void findLootTables(Tag tag, Set<ResourceLocation> output) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("LootTable", Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(compound.getString("LootTable"));
                if (id != null) {
                    output.add(id);
                }
            }
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child != null && (child instanceof CompoundTag || child instanceof ListTag)) {
                    findLootTables(child, output);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                findLootTables(child, output);
            }
        }
    }

    private static ResourceLocation structureId(ResourceLocation resource) {
        String path = resource.getPath();
        if (!path.startsWith("structures/") || !path.endsWith(".nbt")) {
            return null;
        }
        return new ResourceLocation(resource.getNamespace(), path.substring("structures/".length(), path.length() - 4));
    }

    private static Map<ResourceLocation, List<ResourceLocation>> load() {
        if (!Files.isRegularFile(PATH)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject tables = root.getAsJsonObject("tables");
            Map<ResourceLocation, List<ResourceLocation>> result = new HashMap<>();
            if (tables != null) {
                for (Map.Entry<String, JsonElement> entry : tables.entrySet()) {
                    ResourceLocation table = ResourceLocation.tryParse(entry.getKey());
                    if (table == null || !entry.getValue().isJsonArray()) {
                        continue;
                    }
                    List<ResourceLocation> structures = new ArrayList<>();
                    for (JsonElement value : entry.getValue().getAsJsonArray()) {
                        ResourceLocation structure = ResourceLocation.tryParse(value.getAsString());
                        if (structure != null) structures.add(structure);
                    }
                    result.put(table, List.copyOf(structures));
                }
            }
            return Map.copyOf(result);
        } catch (Exception exception) {
            LOGGER.warn("[Solace] Could not read cached loot structure index {}; it will be rebuilt.", PATH, exception);
            return null;
        }
    }

    private static void save(Map<ResourceLocation, List<ResourceLocation>> built) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            JsonObject tables = new JsonObject();
            built.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                JsonArray values = new JsonArray();
                entry.getValue().stream().sorted(Comparator.naturalOrder()).forEach(id -> values.add(id.toString()));
                tables.add(entry.getKey().toString(), values);
            });
            root.add("tables", tables);
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            LOGGER.error("[Solace] Could not save loot structure index {}", PATH, exception);
        }
    }
}
