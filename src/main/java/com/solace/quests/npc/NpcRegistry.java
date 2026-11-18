package com.solace.quests.npc;

import com.mojang.logging.LogUtils;
import com.solace.quests.content.Dialogue.NpcBinding;
import com.solace.quests.content.QuestContent;
import com.solace.quests.state.ServerCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Who each persistent NPC binding ("bind": "first_met") is bound to, world-wide, and what that
 * person was like (a snapshot of name, villager data, trades and gossip).
 *
 * <p>Identity: a bound entity carries {@link #IDENTITY} = the binding id in its persistent data. An
 * entity may also be given an identity in a structure file with the entity tag
 * {@code "solace_identity=<binding id>"} (a captive). When an entity with an identity is freed
 * ({@code solace:free_captive}) or converted by another mod, the new entity takes the identity, the
 * binding moves to it and the snapshot is restored: it is the same person.
 */
public final class NpcRegistry extends SavedData {
    public static final String IDENTITY = "solace_identity";
    private static final String TAG_PREFIX = IDENTITY + "=";
    private static final String NAME = "solace_quest_npcs";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Entity data kept in a snapshot. Missing keys are skipped. */
    private static final String[] SNAPSHOT_KEYS = {"CustomName", "CustomNameVisible", "VillagerData", "Offers",
            "Xp", "Gossips"};

    public static final class Entry {
        public UUID uuid;
        public String dimension = "";
        public BlockPos pos = BlockPos.ZERO;
        public CompoundTag snapshot = new CompoundTag();
    }

    private final Map<String, Entry> entries = new TreeMap<>();

    public static NpcRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NpcRegistry::load, NpcRegistry::new, NAME);
    }

    public Map<String, Entry> all() {
        return java.util.Collections.unmodifiableMap(entries);
    }

    public UUID bound(ResourceLocation binding) {
        Entry entry = entries.get(binding.toString());
        return entry == null ? null : entry.uuid;
    }

    /** The identity an entity carries (persistent data, else a "solace_identity=" entity tag), or null. */
    public static String identity(Entity entity) {
        String id = entity.getPersistentData().getString(IDENTITY);
        if (!id.isEmpty()) {
            return id;
        }
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return tag.substring(TAG_PREFIX.length());
            }
        }
        return null;
    }

    /**
     * May this entity speak for the binding? Non-persistent bindings accept anyone (type and "when" are
     * checked by the caller). A persistent binding accepts only its bound entity; while unbound, the
     * first entity accepted here becomes the bound one ("first met") when {@code claim} is true.
     */
    public static boolean accepts(MinecraftServer server, NpcBinding binding, Entity entity, boolean claim) {
        if (!binding.firstMet()) {
            return true;
        }
        NpcRegistry registry = get(server);
        UUID bound = registry.bound(binding.id());
        if (bound != null) {
            if (bound.equals(entity.getUUID())) {
                if (claim) {
                    registry.remember(binding.id().toString(), entity);
                }
                return true;
            }
            return false;
        }
        // An entity already bound to another persistent binding cannot also be this one.
        String other = identity(entity);
        if (other != null && !other.equals(binding.id().toString()) && registry.entries.containsKey(other)
                && entity.getUUID().equals(registry.entries.get(other).uuid)) {
            return false;
        }
        if (claim) {
            registry.bind(binding.id().toString(), entity, false);
        }
        return true;
    }

    /** Binds {@code entity} to the binding; with {@code restore}, applies the saved snapshot to it first. */
    public void bind(String binding, Entity entity, boolean restore) {
        Entry entry = entries.computeIfAbsent(binding, key -> new Entry());
        if (restore && !entry.snapshot.isEmpty()) {
            restore(entity, entry.snapshot);
        }
        entry.uuid = entity.getUUID();
        entity.getPersistentData().putString(IDENTITY, binding);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        remember(binding, entity);
    }

    public boolean unbind(String binding) {
        Entry entry = entries.get(binding);
        if (entry == null || entry.uuid == null) {
            return false;
        }
        entry.uuid = null;
        setDirty();
        return true;
    }

    /** Updates the snapshot and last known position of the bound entity. */
    public void remember(String binding, Entity entity) {
        Entry entry = entries.computeIfAbsent(binding, key -> new Entry());
        CompoundTag full = entity.saveWithoutId(new CompoundTag());
        CompoundTag snapshot = new CompoundTag();
        for (String key : SNAPSHOT_KEYS) {
            if (full.contains(key)) {
                snapshot.put(key, full.get(key).copy());
            }
        }
        entry.snapshot = snapshot;
        entry.dimension = entity.level().dimension().location().toString();
        entry.pos = entity.blockPosition();
        setDirty();
    }

    /** Only the last known position of the bound entity (cheap; markers call it every second). */
    public void seen(String binding, Entity entity) {
        Entry entry = entries.get(binding);
        if (entry != null && entity.getUUID().equals(entry.uuid) && !entity.blockPosition().equals(entry.pos)) {
            entry.dimension = entity.level().dimension().location().toString();
            entry.pos = entity.blockPosition();
            setDirty();
        }
    }

    /** Applies snapshot keys to a live entity, keeping its own position, UUID and everything else. */
    private static void restore(Entity entity, CompoundTag snapshot) {
        CompoundTag full = entity.saveWithoutId(new CompoundTag());
        full.merge(snapshot.copy());
        UUID uuid = entity.getUUID();
        entity.load(full);
        entity.setUUID(uuid);
    }

    /** The bound entity if it is loaded in any level, else null. */
    public Entity entity(MinecraftServer server, ResourceLocation binding) {
        UUID uuid = bound(binding);
        if (uuid == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /** A bound entity was removed for good (killed, discarded): the binding is free for the next one met. */
    public void removed(Entity entity) {
        String identity = identity(entity);
        if (identity == null) {
            return;
        }
        Entry entry = entries.get(identity);
        if (entry != null && entity.getUUID().equals(entry.uuid)) {
            remember(identity, entity);
            entry.uuid = null;
            setDirty();
        }
    }

    /**
     * {@code from} was replaced by {@code to} (a conversion by vanilla or another mod). The identity moves
     * along; if {@code to} can speak for the binding again, it becomes the bound entity. A villager that
     * comes out jobless gets the saved snapshot back (a freed captive); one that kept its data keeps it.
     */
    public void converted(Entity from, Entity to) {
        String identity = identity(from);
        if (identity == null) {
            return;
        }
        to.getPersistentData().putString(IDENTITY, identity);
        NpcBinding binding = binding(identity);
        if (binding == null || binding.entity() == null || !binding.entity().matches(to)) {
            return;
        }
        boolean jobless = to instanceof Villager villager
                && villager.getVillagerData().getProfession() == VillagerProfession.NONE;
        bind(identity, to, jobless);
    }

    /**
     * Replaces a captive with a free entity of {@code into} that has the captive's identity. Returns the new
     * entity, or null if it could not be created. {@code profession} (may be null) applies to a villager
     * when there is no snapshot to restore.
     */
    public Entity free(Entity captive, ResourceLocation into, ResourceLocation profession) {
        if (!(captive.level() instanceof ServerLevel level)) {
            return null;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(into);
        Entity fresh = type == null ? null : type.create(level);
        if (fresh == null) {
            LOGGER.warn("[Solace Quests] Cannot free a captive into {}: no such entity", into);
            return null;
        }
        fresh.moveTo(captive.getX(), captive.getY(), captive.getZ(), captive.getYRot(), captive.getXRot());
        if (fresh instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(captive.blockPosition()), MobSpawnType.CONVERSION,
                    null, null);
        }
        if (captive.hasCustomName()) {
            fresh.setCustomName(captive.getCustomName());
        }
        String identity = identity(captive);
        Entry entry = identity == null ? null : entries.get(identity);
        if ((entry == null || entry.snapshot.isEmpty()) && profession != null && fresh instanceof Villager villager) {
            VillagerProfession job = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(profession);
            if (job != null) {
                villager.setVillagerData(villager.getVillagerData().setProfession(job));
            }
        }
        level.addFreshEntity(fresh);
        if (identity != null) {
            if (binding(identity) != null) {
                bind(identity, fresh, true);
            } else {
                fresh.getPersistentData().putString(IDENTITY, identity);
            }
        } else if (fresh instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        captive.discard();
        return fresh;
    }

    private static NpcBinding binding(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : QuestContent.current.npcs.get(key);
    }

    /** The nearest entity within {@code radius} that matches the binding (and its bound entity, if any). */
    public static Entity nearest(ServerCtx ctx, NpcBinding binding, double radius) {
        if (binding.entity() == null) {
            return null;
        }
        Object outer = ctx.entity;
        Entity nearest = null;
        try {
            for (Entity entity : ctx.player.level().getEntities(ctx.player, ctx.player.getBoundingBox().inflate(radius),
                    binding.entity()::matches)) {
                ctx.entity = entity;
                if (entity.distanceToSqr(ctx.player) <= radius * radius
                        && (binding.when() == null || binding.when().test(ctx))
                        && accepts(ctx.player.server, binding, entity, false)
                        && (nearest == null || entity.distanceToSqr(ctx.player) < nearest.distanceToSqr(ctx.player))) {
                    nearest = entity;
                }
            }
        } finally {
            ctx.entity = outer;
        }
        return nearest;
    }

    private static NpcRegistry load(CompoundTag tag) {
        NpcRegistry registry = new NpcRegistry();
        for (String key : tag.getAllKeys()) {
            CompoundTag e = tag.getCompound(key);
            Entry entry = new Entry();
            if (e.hasUUID("uuid")) {
                entry.uuid = e.getUUID("uuid");
            }
            entry.dimension = e.getString("dimension");
            entry.pos = BlockPos.of(e.getLong("pos"));
            entry.snapshot = e.getCompound("snapshot");
            registry.entries.put(key, entry);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        entries.forEach((key, entry) -> {
            CompoundTag e = new CompoundTag();
            if (entry.uuid != null) {
                e.putUUID("uuid", entry.uuid);
            }
            e.putString("dimension", entry.dimension);
            e.putLong("pos", entry.pos.asLong());
            e.put("snapshot", entry.snapshot);
            tag.put(key, e);
        });
        return tag;
    }

    /** For "npc" markers when the bound entity is not loaded: where it was last seen. */
    public String lastSeen(ResourceLocation binding) {
        Entry entry = entries.get(binding.toString());
        return entry == null || entry.dimension.isEmpty() ? null
                : entry.dimension + " " + entry.pos.getX() + " " + entry.pos.getY() + " " + entry.pos.getZ();
    }
}
