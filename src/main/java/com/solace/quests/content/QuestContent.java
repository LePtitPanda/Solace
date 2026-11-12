package com.solace.quests.content;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One immutable snapshot of all loaded quest content. Swapped atomically on reload. */
public final class QuestContent {
    public static final List<String> KINDS = List.of("chapters", "quests");
    public static final QuestContent EMPTY = new QuestContent(Map.of(), List.of());
    public static volatile QuestContent current = EMPTY;

    public record Key(String kind, ResourceLocation id) {
    }

    /** A file as read from disk: its JSON, or why it could not be read. */
    public record Raw(JsonElement json, String error) {
    }

    private record Entry(Object definition, List<In.Ref> refs) {
    }

    public final Map<ResourceLocation, Chapter> chapters = new TreeMap<>();
    public final Map<ResourceLocation, Quest> quests = new TreeMap<>();
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
            }
        }
    }

    public long errorCount() {
        return problems.stream().filter(Problem::error).count();
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
                In in = new In(raw.json().getAsJsonObject(), "$", out);
                Object definition = key.kind().equals("chapters") ? QuestParser.chapter(key.id(), in)
                        : QuestParser.quest(key.id(), in);
                entry = new Entry(definition, List.copyOf(out.refs));
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
