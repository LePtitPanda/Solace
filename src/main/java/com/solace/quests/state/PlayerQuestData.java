package com.solace.quests.state;

import com.solace.quests.content.Quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * All quest state of one progress owner (a player in v1). State for ids that no longer exist in
 * the loaded content is kept untouched ("orphaned") and comes back if the id returns.
 */
public final class PlayerQuestData {
    public enum Status { ACTIVE, COMPLETED, FAILED }

    public record History(long time, String phase, String via) {
    }

    /** An objective of an active quest's current phase. */
    public record Active(Quest quest, Quest.Objective objective) {
    }

    public static final class QuestState {
        public Status status = Status.ACTIVE;
        public String phase = "";
        public final Map<String, Integer> progress = new TreeMap<>();
        public long startedAt;
        public int completions;
        public long cooldownUntil;
        public final Set<String> claimed = new TreeSet<>();
        /** Objective id -> keys already counted, for objectives with "distinct". */
        public final Map<String, Set<String>> seen = new TreeMap<>();
        /** The branch path the player took, oldest first. */
        public final List<History> history = new ArrayList<>();

        public void log(long time, String phase, String via) {
            history.add(new History(time, phase, via));
            if (history.size() > 200) {
                history.remove(0);
            }
        }
    }

    public final Map<ResourceLocation, QuestState> quests = new LinkedHashMap<>();
    public final Set<String> flags = new TreeSet<>();
    public final Map<String, Integer> variables = new TreeMap<>();
    /** Quests and chapters force-unlocked by rewards, bypassing their requirements. */
    public final Set<ResourceLocation> unlocked = new TreeSet<>();
    public final Map<ResourceLocation, Integer> reputation = new TreeMap<>();
    /** "dialogue#node" or "dialogue#node/choice" -> game time until which it is on cooldown. */
    public final Map<String, Long> cooldowns = new TreeMap<>();
    /** Quests shown in the HUD tracker, at most {@link QuestLogic#MAX_TRACKED}. */
    public final List<ResourceLocation> tracked = new ArrayList<>();
    /** markers/ entries switched on by dialogue or phase actions. */
    public final Set<ResourceLocation> markers = new TreeSet<>();
    /** Resolved positions worth remembering: "structure:..." and "npc:..." -> "dimension x y z". */
    public final Map<String, String> positions = new TreeMap<>();
    /** Dialogue lines the player has seen, as text-component JSON, newest last. */
    public final List<String> log = new ArrayList<>();
    /** Active objectives by type; rebuilt lazily by the engine, cleared on every change. Not saved. */
    public Map<String, List<Active>> index;
    /** The content snapshot {@link #index} was built from; a reload makes it stale. */
    public Object indexContent;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag questsTag = new CompoundTag();
        quests.forEach((id, state) -> {
            CompoundTag q = new CompoundTag();
            q.putString("status", state.status.name());
            q.putString("phase", state.phase);
            CompoundTag progress = new CompoundTag();
            state.progress.forEach(progress::putInt);
            q.put("progress", progress);
            q.putLong("startedAt", state.startedAt);
            q.putInt("completions", state.completions);
            q.putLong("cooldownUntil", state.cooldownUntil);
            q.put("claimed", strings(state.claimed));
            if (!state.seen.isEmpty()) {
                CompoundTag seen = new CompoundTag();
                state.seen.forEach((objective, keys) -> seen.put(objective, strings(keys)));
                q.put("seen", seen);
            }
            ListTag history = new ListTag();
            for (History entry : state.history) {
                CompoundTag h = new CompoundTag();
                h.putLong("time", entry.time);
                h.putString("phase", entry.phase);
                h.putString("via", entry.via);
                history.add(h);
            }
            q.put("history", history);
            questsTag.put(id.toString(), q);
        });
        tag.put("quests", questsTag);
        tag.put("flags", strings(flags));
        CompoundTag variablesTag = new CompoundTag();
        variables.forEach(variablesTag::putInt);
        tag.put("variables", variablesTag);
        tag.put("unlocked", strings(unlocked.stream().map(ResourceLocation::toString).toList()));
        CompoundTag reputationTag = new CompoundTag();
        reputation.forEach((id, value) -> reputationTag.putInt(id.toString(), value));
        tag.put("reputation", reputationTag);
        CompoundTag cooldownsTag = new CompoundTag();
        cooldowns.forEach(cooldownsTag::putLong);
        tag.put("cooldowns", cooldownsTag);
        tag.put("tracked", strings(tracked.stream().map(ResourceLocation::toString).toList()));
        tag.put("markers", strings(markers.stream().map(ResourceLocation::toString).toList()));
        CompoundTag positionsTag = new CompoundTag();
        positions.forEach(positionsTag::putString);
        tag.put("positions", positionsTag);
        tag.put("log", strings(log));
        return tag;
    }

    public static PlayerQuestData load(CompoundTag tag) {
        PlayerQuestData data = new PlayerQuestData();
        CompoundTag questsTag = tag.getCompound("quests");
        for (String key : questsTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) {
                continue;
            }
            CompoundTag q = questsTag.getCompound(key);
            QuestState state = new QuestState();
            try {
                state.status = Status.valueOf(q.getString("status"));
            } catch (IllegalArgumentException ignored) {
            }
            state.phase = q.getString("phase");
            CompoundTag progress = q.getCompound("progress");
            progress.getAllKeys().forEach(objective -> state.progress.put(objective, progress.getInt(objective)));
            state.startedAt = q.getLong("startedAt");
            state.completions = q.getInt("completions");
            state.cooldownUntil = q.getLong("cooldownUntil");
            q.getList("claimed", Tag.TAG_STRING).forEach(entry -> state.claimed.add(entry.getAsString()));
            CompoundTag seen = q.getCompound("seen");
            for (String objective : seen.getAllKeys()) {
                Set<String> keys = new TreeSet<>();
                seen.getList(objective, Tag.TAG_STRING).forEach(entry -> keys.add(entry.getAsString()));
                state.seen.put(objective, keys);
            }
            for (Tag entry : q.getList("history", Tag.TAG_COMPOUND)) {
                CompoundTag h = (CompoundTag) entry;
                state.history.add(new History(h.getLong("time"), h.getString("phase"), h.getString("via")));
            }
            data.quests.put(id, state);
        }
        tag.getList("flags", Tag.TAG_STRING).forEach(entry -> data.flags.add(entry.getAsString()));
        CompoundTag variablesTag = tag.getCompound("variables");
        variablesTag.getAllKeys().forEach(name -> data.variables.put(name, variablesTag.getInt(name)));
        for (Tag entry : tag.getList("unlocked", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) {
                data.unlocked.add(id);
            }
        }
        CompoundTag reputationTag = tag.getCompound("reputation");
        for (String key : reputationTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                data.reputation.put(id, reputationTag.getInt(key));
            }
        }
        CompoundTag cooldownsTag = tag.getCompound("cooldowns");
        cooldownsTag.getAllKeys().forEach(key -> data.cooldowns.put(key, cooldownsTag.getLong(key)));
        ids(tag, "tracked", data.tracked);
        ids(tag, "markers", data.markers);
        CompoundTag positionsTag = tag.getCompound("positions");
        positionsTag.getAllKeys().forEach(key -> data.positions.put(key, positionsTag.getString(key)));
        tag.getList("log", Tag.TAG_STRING).forEach(entry -> data.log.add(entry.getAsString()));
        return data;
    }

    private static void ids(CompoundTag tag, String key, java.util.Collection<ResourceLocation> into) {
        for (Tag entry : tag.getList(key, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) {
                into.add(id);
            }
        }
    }

    private static ListTag strings(Iterable<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }
}
