package com.solace.quests.content;

import com.google.gson.JsonElement;
import com.solace.quests.content.Dialogue.NpcBinding;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One immutable snapshot of all loaded quest content. Swapped atomically on reload. */
public final class QuestContent {
    public static final List<String> KINDS = List.of("chapters", "quests", "dialogues", "npcs", "factions", "markers",
            "loot", "recipe_locks", "trades", "block_locks");
    public static final QuestContent EMPTY = new QuestContent(Map.of(), List.of());
    public static volatile QuestContent current = EMPTY;

    public record Key(String kind, ResourceLocation id) {
    }

    /** A file as read from disk: its JSON, or why it could not be read. */
    public record Raw(JsonElement json, String error) {
    }

    private record Entry(Object definition, List<In.Ref> refs, JsonElement json) {
    }

    public final Map<ResourceLocation, Chapter> chapters = new TreeMap<>();
    public final Map<ResourceLocation, Quest> quests = new TreeMap<>();
    public final Map<ResourceLocation, Dialogue> dialogues = new TreeMap<>();
    public final Map<ResourceLocation, NpcBinding> npcs = new TreeMap<>();
    public final Map<ResourceLocation, Faction> factions = new TreeMap<>();
    public final Map<ResourceLocation, Marker> markers = new TreeMap<>();
    public final Map<ResourceLocation, Locks.QuestLoot> loot = new TreeMap<>();
    public final Map<ResourceLocation, Locks.RecipeLock> recipeLocks = new TreeMap<>();
    public final Map<ResourceLocation, Locks.TradeLock> tradeLocks = new TreeMap<>();
    public final Map<ResourceLocation, Locks.BlockLock> blockLocks = new TreeMap<>();
    /** Highest priority first. */
    public final List<NpcBinding> bindings = new ArrayList<>();
    public final List<Quest> autoStart = new ArrayList<>();
    public final Set<String> objectiveTypes = new HashSet<>();
    public final List<Problem> problems;
    private final Map<Key, Entry> entries;

    private QuestContent(Map<Key, Entry> entries, List<Problem> problems) {
        this.entries = entries;
        this.problems = List.copyOf(problems);
        for (Entry entry : entries.values()) {
            if (entry.definition instanceof Chapter chapter) {
                chapters.put(chapter.id(), chapter);
            } else if (entry.definition instanceof Quest quest) {
                quests.put(quest.id(), quest);
                if (quest.autoStart()) {
                    autoStart.add(quest);
                }
                quest.phases().forEach(phase -> phase.objectives().forEach(o -> objectiveTypes.add(o.type())));
            } else if (entry.definition instanceof Dialogue dialogue) {
                dialogues.put(dialogue.id(), dialogue);
            } else if (entry.definition instanceof NpcBinding binding) {
                npcs.put(binding.id(), binding);
                bindings.add(binding);
            } else if (entry.definition instanceof Faction faction) {
                factions.put(faction.id(), faction);
            } else if (entry.definition instanceof Locks.QuestLoot questLoot) {
                loot.put(questLoot.id(), questLoot);
            } else if (entry.definition instanceof Locks.RecipeLock lock) {
                recipeLocks.put(lock.id(), lock);
            } else if (entry.definition instanceof Locks.TradeLock lock) {
                tradeLocks.put(lock.id(), lock);
            } else if (entry.definition instanceof Locks.BlockLock lock) {
                blockLocks.put(lock.id(), lock);
            }
        }
        entries.forEach((key, entry) -> {
            if (entry.definition instanceof Marker marker) {
                markers.put(key.id(), marker);
            }
        });
        bindings.sort(Comparator.comparingInt(NpcBinding::priority).reversed());
    }

    public long errorCount() {
        return problems.stream().filter(Problem::error).count();
    }

    /** The JSON of every applied entry, for the editor. */
    public Map<Key, JsonElement> sources() {
        Map<Key, JsonElement> sources = new LinkedHashMap<>();
        entries.forEach((key, entry) -> sources.put(key, entry.json));
        return sources;
    }

    private static Object parse(Key key, In in) {
        return switch (key.kind()) {
            case "chapters" -> QuestParser.chapter(key.id(), in);
            case "quests" -> QuestParser.quest(key.id(), in);
            case "dialogues" -> DialogueParser.dialogue(key.id(), in);
            case "npcs" -> DialogueParser.npc(key.id(), in);
            case "factions" -> QuestParser.faction(key.id(), in);
            case "loot" -> Locks.loot(key.id(), in);
            case "recipe_locks" -> Locks.recipeLock(key.id(), in);
            case "trades" -> Locks.tradeLock(key.id(), in);
            case "block_locks" -> Locks.blockLock(key.id(), in);
            default -> QuestParser.markerFile(key.id(), in);
        };
    }

    /** Validates one file on its own, without applying it (editor "validate" and pre-save check). */
    public static List<Problem> check(Key key, JsonElement json, In.Lookup lookup) {
        In.Out out = new In.Out(key.kind(), key.id(), lookup);
        if (json == null || !json.isJsonObject()) {
            out.problems.add(new Problem(key.kind(), key.id().toString(), "$", true, "expected a JSON object"));
        } else {
            parse(key, new In(json.getAsJsonObject(), "$", out));
        }
        return out.problems;
    }

    /**
     * Validates every file on its own, then the references between them. A file with an error is
     * replaced by its version from {@code previous} (or left out); the other files are unaffected.
     */
    public static QuestContent build(Map<Key, Raw> files, QuestContent previous, In.Lookup lookup) {
        Map<Key, Entry> entries = new LinkedHashMap<>();
        List<Problem> problems = new ArrayList<>();
        for (Map.Entry<Key, Raw> file : files.entrySet()) {
            Key key = file.getKey();
            In.Out out = new In.Out(key.kind(), key.id(), lookup);
            Entry entry = null;
            Raw raw = file.getValue();
            if (raw.error() != null) {
                out.problems.add(new Problem(key.kind(), key.id().toString(), "$", true, raw.error()));
            } else if (!raw.json().isJsonObject()) {
                out.problems.add(new Problem(key.kind(), key.id().toString(), "$", true, "expected a JSON object"));
            } else {
                Object definition = parse(key, new In(raw.json().getAsJsonObject(), "$", out));
                entry = new Entry(definition, List.copyOf(out.refs), raw.json());
            }
            problems.addAll(out.problems);
            if (out.failed()) {
                entry = fallback(key, previous, problems);
            }
            if (entry != null) {
                entries.put(key, entry);
            }
        }

        // References: a dangling entry falls back once to its previous version, then is dropped.
        // Repeats because dropping one entry can orphan another.
        Set<Key> reverted = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Key key : List.copyOf(entries.keySet())) {
                Entry entry = entries.get(key);
                List<In.Ref> broken = entry.refs.stream()
                        .filter(ref -> !entries.containsKey(new Key(ref.kind(), ref.id()))).toList();
                if (broken.isEmpty()) {
                    continue;
                }
                changed = true;
                broken.forEach(ref -> problems.add(new Problem(key.kind(), key.id().toString(), ref.path(), true,
                        "unknown " + ref.kind() + " entry \"" + ref.id() + "\"")));
                Entry old = previous.entries.get(key);
                if (old != null && old != entry && reverted.add(key)) {
                    entries.put(key, fallback(key, previous, problems));
                } else {
                    entries.remove(key);
                }
            }
        }
        return new QuestContent(entries, problems);
    }

    private static Entry fallback(Key key, QuestContent previous, List<Problem> problems) {
        Entry old = previous.entries.get(key);
        problems.add(new Problem(key.kind(), key.id().toString(), "$", false,
                old != null ? "kept the previous valid version of this file" : "file ignored until it is fixed"));
        return old;
    }
}
