package com.solace.quests.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.solace.quests.content.QuestContent.Key;
import com.solace.quests.content.QuestContent.Raw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads data/&lt;ns&gt;/solace_quests/&lt;kind&gt;/**.json through ResourceManager streams, then
 * config/solace/quests/&lt;ns&gt;/&lt;kind&gt;/**.json, which wins on the same id.
 */
public final class QuestContentLoader extends SimplePreparableReloadListener<Map<Key, Raw>> {
    public static final int MAX_FILE_BYTES = 512 * 1024;
    public static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    public static final int MAX_FILES = 20_000;
    public static final int MAX_DEPTH = 64;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOT = "solace_quests";

    @Override
    protected Map<Key, Raw> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return read(manager, looseRoot());
    }

    @Override
    protected void apply(Map<Key, Raw> files, ResourceManager manager, ProfilerFiller profiler) {
        apply(files);
    }

    /** Hot reload without a full /reload. Server thread only. */
    public static QuestContent reloadNow(ResourceManager manager) {
        return apply(read(manager, looseRoot()));
    }

    private static QuestContent apply(Map<Key, Raw> files) {
        QuestContent content = QuestContent.build(files, QuestContent.current, QuestContentLoader::exists);
        QuestContent.current = content;
        LOGGER.info("[Solace Quests] Loaded {} chapters, {} quests, {} dialogues, {} npcs, {} factions, {} markers "
                        + "({} errors, {} warnings)", content.chapters.size(), content.quests.size(),
                content.dialogues.size(), content.npcs.size(), content.factions.size(), content.markers.size(),
                content.errorCount(), content.problems.size() - content.errorCount());
        content.problems.forEach(problem -> {
            if (problem.error()) {
                LOGGER.error("[Solace Quests] {}", problem);
            } else {
                LOGGER.warn("[Solace Quests] {}", problem);
            }
        });
        return content;
    }

    public static boolean exists(String registry, ResourceLocation id) {
        return switch (registry) {
            case "item" -> ForgeRegistries.ITEMS.containsKey(id);
            case "block" -> ForgeRegistries.BLOCKS.containsKey(id);
            case "entity_type" -> ForgeRegistries.ENTITY_TYPES.containsKey(id);
            default -> true;
        };
    }

    public static Path looseRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("solace").resolve("quests");
    }

    static Map<Key, Raw> read(ResourceManager manager, Path looseRoot) {
        Map<Key, Raw> files = new LinkedHashMap<>();
        long[] budget = {MAX_TOTAL_BYTES};
        for (String kind : QuestContent.KINDS) {
            String prefix = ROOT + "/" + kind + "/";
            Map<ResourceLocation, Resource> found = manager.listResources(ROOT + "/" + kind,
                    location -> location.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> resource : found.entrySet()) {
                String path = resource.getKey().getPath();
                Key key = new Key(kind, new ResourceLocation(resource.getKey().getNamespace(),
                        path.substring(prefix.length(), path.length() - ".json".length())));
                if (files.size() >= MAX_FILES) {
                    files.put(key, new Raw(null, "too many content files (limit " + MAX_FILES + ")"));
                    continue;
                }
                try (InputStream stream = resource.getValue().open()) {
                    files.put(key, parse(stream, budget));
                } catch (IOException exception) {
                    files.put(key, new Raw(null, "could not read file: " + exception.getMessage()));
                }
            }
        }
        readLoose(looseRoot, files, budget);
        return files;
    }

    private static void readLoose(Path root, Map<Key, Raw> files, long[] budget) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                Path relative = root.relativize(file);
                String name = relative.toString().replace('\\', '/');
                // <namespace>/<kind>/<path...>.json
                if (relative.getNameCount() < 3 || !name.endsWith(".json")
                        || !QuestContent.KINDS.contains(relative.getName(1).toString())) {
                    continue;
                }
                String namespace = relative.getName(0).toString();
                String kind = relative.getName(1).toString();
                String path = name.substring(namespace.length() + kind.length() + 2, name.length() - ".json".length());
                ResourceLocation id = ResourceLocation.tryBuild(namespace, path);
                if (id == null) {
                    LOGGER.warn("[Solace Quests] Skipping {}: not a valid id", file);
                    continue;
                }
                Key key = new Key(kind, id);
                Raw raw;
                try (InputStream stream = Files.newInputStream(file)) {
                    raw = parse(stream, budget);
                } catch (IOException exception) {
                    raw = new Raw(null, "could not read file: " + exception.getMessage());
                }
                // {"removed": true} hides a datapack entry of the same id.
                if (raw.json() != null && raw.json().isJsonObject() && raw.json().getAsJsonObject().size() == 1
                        && raw.json().getAsJsonObject().has("removed")) {
                    files.remove(key);
                } else {
                    files.put(key, raw);
                }
            }
        } catch (IOException exception) {
            LOGGER.error("[Solace Quests] Could not scan {}", root, exception);
        }
    }

    /** Limits are enforced on the bytes actually read; a datapack resource has no file path to ask. */
    static Raw parse(InputStream stream, long[] budget) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length > MAX_FILE_BYTES) {
            return new Raw(null, "file is larger than " + MAX_FILE_BYTES / 1024 + " KiB");
        }
        budget[0] -= bytes.length;
        if (budget[0] < 0) {
            return new Raw(null, "total content size limit of " + MAX_TOTAL_BYTES / (1024 * 1024) + " MiB exceeded");
        }
        if (tooDeep(bytes)) {
            return new Raw(null, "JSON is nested deeper than " + MAX_DEPTH + " levels");
        }
        try {
            JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return new Raw(json, null);
        } catch (RuntimeException exception) {
            return new Raw(null, "invalid JSON: " + exception.getMessage());
        }
    }

    /** Gson recurses per nesting level, so a file of "[[[[..." would overflow the stack. */
    private static boolean tooDeep(byte[] bytes) {
        int depth = 0;
        boolean string = false;
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (string) {
                if (b == '\\') {
                    i++;
                } else if (b == '"') {
                    string = false;
                }
            } else if (b == '"') {
                string = true;
            } else if (b == '{' || b == '[') {
                if (++depth > MAX_DEPTH) {
                    return true;
                }
            } else if (b == '}' || b == ']') {
                depth--;
            }
        }
        return false;
    }
}
