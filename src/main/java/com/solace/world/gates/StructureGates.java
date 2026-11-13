package com.solace.world.gates;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.solace.world.gates.StructureGateConfig.Entry;
import com.solace.world.gates.StructureGateConfig.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structure gates: a gated structure only generates while its gate is open. The config says which
 * structures are gated; {@link StructureGateData} remembers what happened in this world. The
 * command, the quest effects and the KubeJS API all call {@link #open}/{@link #close}/{@link #reset}.
 * See docs/structure-gates.md.
 */
public final class StructureGates {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> LOCATING = ThreadLocal.withInitial(() -> false);
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static volatile List<Entry> entries = List.of();
    /** Config expanded to structure ids (tags resolved once the registry exists). */
    private static volatile Map<ResourceLocation, Entry> gated = Map.of();
    private static volatile StructureGateData data;
    /** Bumped on every gate change, so cached locate results know they are stale. */
    private static volatile int version;

    private StructureGates() {
    }

    public static void init() {
        entries = StructureGateConfig.load();
        expand();
        MinecraftForge.EVENT_BUS.addListener(StructureGates::onLevelLoad);
        MinecraftForge.EVENT_BUS.addListener(StructureGates::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(StructureGates::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(StructureGates::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                StructureGateCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.register(StructureLocators.class);
    }

    // Lifecycle --------------------------------------------------------------------------------

    private static void onLevelLoad(LevelEvent.Load event) {
        // The overworld loads before any chunk is generated, so worldgen never runs without state.
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            data = StructureGateData.get(level);
            expand();
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            data = null;
        }
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            expand();
        }
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
                StructureGates.reload();
            }
        });
    }

    /** Re-reads the config. Tags are expanded again when the server's tags are (re)bound. */
    public static void reload() {
        entries = StructureGateConfig.load();
        WARNED.clear();
        expand();
    }

    private static void expand() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Registry<Structure> registry = server == null ? null
                : server.registryAccess().registry(Registries.STRUCTURE).orElse(null);
        Map<ResourceLocation, Entry> expanded = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (!entry.tag()) {
                expanded.putIfAbsent(entry.id(), entry);
                if (registry != null && !registry.containsKey(entry.id()) && WARNED.add(entry.key())) {
                    LOGGER.warn("[Solace] Structure gate {}: no such structure (yet); kept, applies once it exists",
                            entry.key());
                }
                continue;
            }
            if (registry == null) {
                continue;
            }
            Optional<HolderSet.Named<Structure>> members = registry.getTag(TagKey.create(Registries.STRUCTURE, entry.id()));
            if (members.isEmpty() || members.get().size() == 0) {
                if (WARNED.add(entry.key())) {
                    LOGGER.warn("[Solace] Structure gate {}: tag is missing or empty; kept, applies once it has members",
                            entry.key());
                }
                continue;
            }
            for (Holder<Structure> holder : members.get()) {
                holder.unwrapKey().ifPresent(key -> expanded.putIfAbsent(key.location(), entry));
            }
        }
        gated = Map.copyOf(expanded);
        version++;
    }

    // Worldgen and locate hooks (any thread) -----------------------------------------------------

    @Nullable
    private static ResourceLocation id(Holder<Structure> holder) {
        return holder.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    public static boolean isGated(@Nullable ResourceLocation id) {
        return id != null && gated.containsKey(id);
    }

    public static int version() {
        return version;
    }

    /** False for a gated structure whose gate is closed (or before the world's gate state loaded). */
    public static boolean mayGenerate(Holder<Structure> holder) {
        ResourceLocation id = id(holder);
        Entry entry = id == null ? null : gated.get(id);
        if (entry == null) {
            return true;
        }
        StructureGateData state = data;
        return state != null && state.isOpen(id, entry);
    }

    /** Called when a valid start is about to be placed; a once gate is claimed here, atomically. */
    public static boolean claim(Holder<Structure> holder, LevelAccessor level, StructureStart start) {
        ResourceLocation id = id(holder);
        Entry entry = id == null ? null : gated.get(id);
        if (entry == null) {
            return true;
        }
        StructureGateData state = data;
        if (state == null) {
            return false;
        }
        ResourceKey<Level> dimension = level instanceof WorldGenLevel worldGen ? worldGen.getLevel().dimension()
                : Level.OVERWORLD;
        BlockPos pos = start.getBoundingBox().getCenter();
        Mode before = state.mode(id, entry);
        if (!state.claim(id, entry, new StructureGateData.SavedPos(dimension.location(), pos))) {
            return false;
        }
        if (before == Mode.ONCE) {
            version++;
            LOGGER.info("[Solace] Structure gate {}: the one copy generated at {} in {}; gate closed",
                    id, pos.toShortString(), dimension.location());
        }
        return true;
    }

    public static boolean affects(HolderSet<Structure> structures) {
        if (LOCATING.get() || gated.isEmpty()) {
            return false;
        }
        for (Holder<Structure> holder : structures) {
            if (isGated(id(holder))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gate-aware {@code findNearestMapStructure}: closed gates are skipped, generated once-copies
     * answer with their saved position, open gates search normally. The nearest answer wins.
     */
    @Nullable
    public static Pair<BlockPos, Holder<Structure>> locate(ChunkGenerator generator, ServerLevel level,
                                                           HolderSet<Structure> structures, BlockPos from,
                                                           int radius, boolean skipKnown) {
        StructureGateData state = data;
        List<Holder<Structure>> searchable = new ArrayList<>();
        Pair<BlockPos, Holder<Structure>> best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Holder<Structure> holder : structures) {
            ResourceLocation id = id(holder);
            Entry entry = id == null ? null : gated.get(id);
            if (entry == null) {
                searchable.add(holder);
                continue;
            }
            if (state == null) {
                continue;
            }
            for (StructureGateData.SavedPos saved : state.positions(id)) {
                double distance = horizontalDistance(saved.pos(), from);
                if (saved.dimension().equals(level.dimension().location()) && distance < bestDistance) {
                    best = Pair.of(saved.pos(), holder);
                    bestDistance = distance;
                }
            }
            if (state.isOpen(id, entry)) {
                searchable.add(holder);
            }
        }
        if (!searchable.isEmpty()) {
            LOCATING.set(true);
            try {
                Pair<BlockPos, Holder<Structure>> found = generator.findNearestMapStructure(level,
                        HolderSet.direct(searchable), from, radius, skipKnown);
                if (found != null && horizontalDistance(found.getFirst(), from) < bestDistance) {
                    best = found;
                }
            } finally {
                LOCATING.set(false);
            }
        }
        return best;
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Nearest structure (id or "#tag") from {@code from}, through the gate-aware search. */
    @Nullable
    public static BlockPos find(ServerLevel level, String structure, BlockPos from, int radius, boolean skipKnown) {
        boolean tag = structure.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? structure.substring(1) : structure);
        if (id == null) {
            return null;
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> set;
        if (tag) {
            Optional<HolderSet.Named<Structure>> named = registry.getTag(TagKey.create(Registries.STRUCTURE, id));
            if (named.isEmpty()) {
                return null;
            }
            set = named.get();
        } else {
            Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id));
            if (holder.isEmpty()) {
                return null;
            }
            set = HolderSet.direct(holder.get());
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, set, from, radius, skipKnown);
        return found == null ? null : found.getFirst();
    }

    // The switch (server thread) ------------------------------------------------------------------

    /** Gated structures matched by an id or "#tag", with the config entry that gates each one. */
    static Map<ResourceLocation, Entry> resolve(MinecraftServer server, String target) {
        Map<ResourceLocation, Entry> matched = new LinkedHashMap<>();
        boolean tag = target.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? target.substring(1) : target);
        if (id == null) {
            return matched;
        }
        Map<ResourceLocation, Entry> current = gated;
        if (!tag) {
            Entry entry = current.get(id);
            if (entry != null) {
                matched.put(id, entry);
            }
            return matched;
        }
        server.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(TagKey.create(Registries.STRUCTURE, id))
                .ifPresent(members -> members.forEach(holder -> {
                    ResourceLocation member = id(holder);
                    if (member != null && current.containsKey(member)) {
                        matched.put(member, current.get(member));
                    }
                }));
        // Config entries that are tags also match by name, even when the tag is not bound yet.
        current.forEach((member, entry) -> {
            if (entry.tag() && entry.id().equals(id)) {
                matched.putIfAbsent(member, entry);
            }
        });
        return matched;
    }

    private static StructureGateData state(MinecraftServer server) {
        StructureGateData state = data;
        return state != null ? state : StructureGateData.get(server.overworld());
    }

    /**
     * Opens every gated structure matched by {@code target}. {@code mode} null = each entry's config
     * mode. Idempotent: an open gate stays as it is and a once gate whose copy exists stays closed.
     * Returns how many gates changed, or -1 when nothing in the config matches.
     */
    public static int open(MinecraftServer server, String target, @Nullable Mode mode) {
        return change(server, target, (state, id, entry) -> state.open(id, entry, mode != null ? mode : entry.mode()));
    }

    public static int close(MinecraftServer server, String target) {
        return change(server, target, (state, id, entry) -> state.close(id, entry));
    }

    /** Back to the config default (closed unless initially_open) and forgets saved positions. */
    public static int reset(MinecraftServer server, String target) {
        return change(server, target, (state, id, entry) -> state.reset(id));
    }

    private interface Change {
        boolean apply(StructureGateData state, ResourceLocation id, Entry entry);
    }

    private static int change(MinecraftServer server, String target, Change change) {
        Map<ResourceLocation, Entry> matched = resolve(server, target);
        if (matched.isEmpty()) {
            return -1;
        }
        StructureGateData state = state(server);
        int changed = 0;
        for (Map.Entry<ResourceLocation, Entry> entry : matched.entrySet()) {
            if (change.apply(state, entry.getKey(), entry.getValue())) {
                changed++;
            }
        }
        if (changed > 0) {
            version++;
        }
        return changed;
    }

    static List<Entry> entries() {
        return entries;
    }

    static Map<ResourceLocation, Entry> gated() {
        return gated;
    }

    /** "open (once)", "closed", plus saved positions: for /solace gates and KubeJS. */
    public static String describe(MinecraftServer server, ResourceLocation id) {
        Entry entry = gated.get(id);
        if (entry == null) {
            return "not gated";
        }
        StructureGateData state = state(server);
        StringBuilder text = new StringBuilder(state.isOpen(id, entry) ? "open (" + state.mode(id, entry).id() + ")"
                : "closed");
        for (StructureGateData.SavedPos pos : state.positions(id)) {
            text.append(" | generated at ").append(pos.pos().toShortString()).append(" in ")
                    .append(pos.dimension());
        }
        return text.toString();
    }

    public static boolean isOpen(MinecraftServer server, ResourceLocation id) {
        Entry entry = gated.get(id);
        return entry == null || state(server).isOpen(id, entry);
    }
}
