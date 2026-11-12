package com.solace.quests.state;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level store of every owner's quest state: data/solace_quests.dat in the overworld. */
public final class QuestSavedData extends SavedData {
    public static final int SCHEMA = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<UUID, PlayerQuestData> owners = new HashMap<>();
    /** Set when the file was written by a newer Solace: kept verbatim and never overwritten. */
    private CompoundTag frozen;

    public static QuestSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestSavedData::load, QuestSavedData::new,
                "solace_quests");
    }

    public static QuestSavedData load(CompoundTag tag) {
        QuestSavedData data = new QuestSavedData();
        int schema = tag.getInt("schema");
        if (schema > SCHEMA) {
            LOGGER.error("[Solace Quests] solace_quests.dat has schema {} but this build only knows {}. "
                    + "Quest progress is NOT loaded and will NOT be saved, to avoid destroying it. Update Solace.",
                    schema, SCHEMA);
            data.frozen = tag.copy();
            return data;
        }
        // Migration hook: when SCHEMA becomes 2, upgrade `tag` here step by step before reading it.
        CompoundTag owners = tag.getCompound("owners");
        for (String key : owners.getAllKeys()) {
            try {
                data.owners.put(UUID.fromString(key), PlayerQuestData.load(owners.getCompound(key)));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("[Solace Quests] Skipping owner with invalid id {}", key);
            }
        }
        return data;
    }

    public boolean frozen() {
        return frozen != null;
    }

    public PlayerQuestData owner(UUID id) {
        return owners.computeIfAbsent(id, key -> new PlayerQuestData());
    }

    public Iterable<PlayerQuestData> all() {
        return owners.values();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (frozen != null) {
            return frozen.copy();
        }
        tag.putInt("schema", SCHEMA);
        CompoundTag ownersTag = new CompoundTag();
        owners.forEach((id, data) -> ownersTag.put(id.toString(), data.save()));
        tag.put("owners", ownersTag);
        return tag;
    }
}
