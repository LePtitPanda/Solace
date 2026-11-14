package com.solace.loot;

import com.solace.config.SolaceConfigPaths;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.solace.performance.ServerWorkScheduler;
import com.solace.mixin.LootContextRandomAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cached, low-impact startup probing for items appended by global loot modifiers. */
public final class LootExtraDetector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FORMAT = 1;
    private static final int SEEDED_PROBES = 16;
    private static final int MAX_PROBES_PER_TICK = 256;
    private static final long MAX_NANOS_PER_TICK = 5_000_000L;
    private static Scanner scanner;
    private static volatile boolean ready;
    private static volatile int progress;

    private LootExtraDetector() {
    }

    public static synchronized void start(MinecraftServer server, boolean force, ServerPlayer requester) {
        if (scanner != null && !force) {
            return;
        }
        String fingerprint;
        try {
            fingerprint = fingerprint(server);
        } catch (Exception exception) {
            LOGGER.error("[Solace] Could not fingerprint the modpack for extra-loot detection", exception);
            fingerprint = "unavailable-" + FORMAT;
        }

        JsonObject saved = LootEditorConfig.extraDetector();
        if (!force && saved.has("complete") && saved.get("complete").getAsBoolean()
                && saved.has("fingerprint") && fingerprint.equals(saved.get("fingerprint").getAsString())) {
            ready = true;
            progress = 100;
            LOGGER.info("[Solace] Reusing cached global loot-extra index ({} table/item associations).",
                    saved.has("associations") ? saved.get("associations").getAsInt() : 0);
            return;
        }

        List<ResourceLocation> tables = server.getLootData().getKeys(LootDataType.TABLE).stream()
                .filter(id -> server.getLootData().getLootTable(id).getParamSet() == LootContextParamSets.CHEST)
                .sorted()
                .toList();
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        if (levels.isEmpty()) {
            levels.add(server.overworld());
        }
        scanner = new Scanner(server, fingerprint, tables, List.copyOf(levels));
        ready = false;
        progress = 0;
        saved.addProperty("format", FORMAT);
        saved.addProperty("fingerprint", fingerprint);
        saved.addProperty("complete", false);
        LootEditorConfig.save();
        LOGGER.info("[Solace] Starting one-time global loot-extra scan: {} chest tables, {} dimensions.",
                tables.size(), levels.size());
        if (requester != null) {
            requester.sendSystemMessage(Component.literal("Solace extra detector started for " + tables.size()
                    + " chest loot tables. It is batched and will be cached."));
        }
    }

    public static synchronized void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || scanner == null || scanner.server != event.getServer()
                || ServerWorkScheduler.isPressured(event.getServer())) {
            return;
        }
        if (scanner.waitingTicks < 200 && event.getServer().getPlayerList().getPlayers().isEmpty()) {
            scanner.waitingTicks++;
            return;
        }
        if (!scanner.announced && !event.getServer().getPlayerList().getPlayers().isEmpty()) {
            scanner.announced = true;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                player.sendSystemMessage(Component.literal("Solace is indexing mod-added chest loot once in the background."));
            }
        }
        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        int work = 0;
        while (scanner != null && work++ < MAX_PROBES_PER_TICK && System.nanoTime() < deadline) {
            if (!scanner.step()) {
                finish();
            }
        }
    }

    public static synchronized boolean isReady() {
        return ready;
    }

    public static synchronized int progress() {
        return progress;
    }

    public static synchronized void reset(MinecraftServer server) {
        if (scanner != null && scanner.server == server) {
            scanner = null;
        }
        ready = false;
        progress = 0;
    }

    private static void finish() {
        Scanner completed = scanner;
        scanner = null;
        if (completed == null) {
            return;
        }
        int added = LootExtraManager.recordDetected(completed.detected);
        int associations = completed.detected.values().stream().mapToInt(Map::size).sum();
        JsonObject saved = LootEditorConfig.extraDetector();
        saved.addProperty("format", FORMAT);
        saved.addProperty("fingerprint", completed.fingerprint);
        saved.addProperty("complete", true);
        saved.addProperty("tables", completed.tables.size());
        saved.addProperty("associations", associations);
        saved.addProperty("newAssociations", added);
        saved.addProperty("probeFailures", completed.failures);
        saved.addProperty("completedAt", Instant.now().toString());
        LootEditorConfig.save();
        ready = true;
        progress = 100;
        String message = "Solace extra detector finished: " + associations + " table/item associations ("
                + added + " newly discovered).";
        LOGGER.info("[Solace] {} Probe failures: {}.", message, completed.failures);
        for (ServerPlayer player : completed.server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static String fingerprint(MinecraftServer server) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        update(digest, "solace-extra-detector:" + FORMAT);
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(info -> info.getModId()))
                .forEach(info -> update(digest, info.getModId() + "=" + info.getVersion()));
        server.getPackRepository().getSelectedIds().stream().sorted().forEach(id -> update(digest, "pack=" + id));
        server.getLootData().getKeys(LootDataType.TABLE).stream().sorted()
                .forEach(id -> update(digest, "table=" + id));
        server.getResourceManager().listResources("loot_modifiers", id -> id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    update(digest, "modifier=" + entry.getKey());
                    try (InputStream input = entry.getValue().open()) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            digest.update(buffer, 0, read);
                        }
                    } catch (Exception exception) {
                        update(digest, "unreadable");
                    }
                });

        Path config = FMLPaths.CONFIGDIR.get();
        if (Files.isDirectory(config)) {
            try (var files = Files.walk(config)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> !(path.getParent().equals(SolaceConfigPaths.dir())
                                && path.getFileName().toString().startsWith("loot-")))
                        .filter(LootExtraDetector::isRelevantConfig)
                        .sorted()
                        .forEach(path -> {
                            update(digest, config.relativize(path).toString().replace('\\', '/'));
                            try (InputStream input = Files.newInputStream(path)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = input.read(buffer)) >= 0) {
                                    digest.update(buffer, 0, read);
                                }
                            } catch (Exception exception) {
                                update(digest, "unreadable");
                            }
                        });
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean isRelevantConfig(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".toml") || name.endsWith(".yaml")
                || name.endsWith(".yml") || name.endsWith(".cfg") || name.endsWith(".properties");
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static final class Scanner {
        private final MinecraftServer server;
        private final String fingerprint;
        private final List<ResourceLocation> tables;
        private final List<ServerLevel> levels;
        private final Map<ResourceLocation, Map<ResourceLocation, ItemStack>> detected = new LinkedHashMap<>();
        private final int probesPerTable;
        private int tableIndex;
        private int probeIndex;
        private int failures;
        private int waitingTicks;
        private boolean announced;

        private Scanner(MinecraftServer server, String fingerprint, List<ResourceLocation> tables,
                        List<ServerLevel> levels) {
            this.server = server;
            this.fingerprint = fingerprint;
            this.tables = tables;
            this.levels = levels;
            this.probesPerTable = 2 + SEEDED_PROBES + levels.size() * 2;
        }

        private boolean step() {
            if (tableIndex >= tables.size()) {
                return false;
            }
            ResourceLocation table = tables.get(tableIndex);
            try {
                Probe probe = probe(probeIndex);
                LootParams.Builder params = new LootParams.Builder(probe.level)
                        .withParameter(LootContextParams.ORIGIN, probe.origin)
                        .withLuck(probe.luck);
                if (probe.player != null) {
                    params.withParameter(LootContextParams.THIS_ENTITY, probe.player);
                }
                LootContext context = new LootContext.Builder(params.create(LootContextParamSets.CHEST))
                        .withOptionalRandomSeed(1L)
                        .withQueriedLootTableId(table)
                        .create(null);
                ((LootContextRandomAccessor) context).solace$setRandom(probe.random);
                ObjectArrayList<ItemStack> output = ForgeHooks.modifyLoot(table, new ObjectArrayList<>(), context);
                for (ItemStack stack : output) {
                    if (!stack.isEmpty()) {
                        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        detected.computeIfAbsent(table, ignored -> new LinkedHashMap<>())
                                .putIfAbsent(item, stack.copy());
                    }
                }
            } catch (RuntimeException | LinkageError exception) {
                failures++;
                if (probeIndex == 0) {
                    LOGGER.debug("[Solace] Extra probe could not evaluate {}", table, exception);
                }
            }

            probeIndex++;
            if (probeIndex >= probesPerTable) {
                probeIndex = 0;
                tableIndex++;
            }
            long done = (long) tableIndex * probesPerTable + probeIndex;
            long total = Math.max(1L, (long) tables.size() * probesPerTable);
            progress = (int) Math.min(99L, done * 100L / total);
            return tableIndex < tables.size();
        }

        private Probe probe(int index) {
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            ServerLevel playerLevel = player == null ? server.overworld() : player.serverLevel();
            Vec3 playerOrigin = player == null
                    ? Vec3.atCenterOf(playerLevel.getSharedSpawnPos()) : player.position();
            float luck = player == null ? 0.0F : player.getLuck();
            if (index == 0) {
                return new Probe(playerLevel, playerOrigin, player, luck, new EdgeRandomSource(false));
            }
            if (index == 1) {
                return new Probe(playerLevel, playerOrigin, player, luck, new EdgeRandomSource(true));
            }
            if (index < 2 + SEEDED_PROBES) {
                long seed = 0x9E3779B97F4A7C15L * (index - 1L) + tableIndex * 0xC2B2AE3D27D4EB4FL;
                return new Probe(playerLevel, playerOrigin, player, luck, RandomSource.create(seed));
            }
            int levelProbe = index - 2 - SEEDED_PROBES;
            ServerLevel level = levels.get(levelProbe / 2);
            BlockPos spawn = level.getSharedSpawnPos();
            return new Probe(level, Vec3.atCenterOf(spawn), null, 0.0F,
                    new EdgeRandomSource((levelProbe & 1) != 0));
        }
    }

    private record Probe(ServerLevel level, Vec3 origin, ServerPlayer player, float luck, RandomSource random) {
    }

    /** Hits probability edges while cycling bounded selections so simple uniqueness loops still advance. */
    private static final class EdgeRandomSource implements RandomSource {
        private final boolean high;
        private long counter;
        private int edgeCalls;

        private EdgeRandomSource(boolean high) {
            this.high = high;
        }

        @Override public RandomSource fork() { return new EdgeRandomSource(high); }
        @Override public PositionalRandomFactory forkPositional() {
            return new PositionalRandomFactory() {
                @Override public RandomSource fromHashOf(String name) { return new EdgeRandomSource(high); }
                @Override public RandomSource at(int x, int y, int z) { return new EdgeRandomSource(high); }
                @Override public void parityConfigString(StringBuilder builder) { builder.append("solace_edge"); }
            };
        }
        @Override public void setSeed(long seed) { counter = seed; edgeCalls = 0; }
        @Override public int nextInt() { return high ? ~(int) counter++ : (int) counter++; }
        @Override public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            int value = (int) Math.floorMod(counter++, bound);
            return high ? bound - 1 - value : value;
        }
        @Override public long nextLong() { return high ? ~counter++ : counter++; }
        @Override public boolean nextBoolean() { return high ? (counter++ & 1L) == 0L : (counter++ & 1L) != 0L; }
        @Override public float nextFloat() {
            boolean useHighEdge = edgeCalls++ < 512 ? high : !high;
            return useHighEdge ? Math.nextDown(1.0F) : 0.0F;
        }
        @Override public double nextDouble() {
            boolean useHighEdge = edgeCalls++ < 512 ? high : !high;
            return useHighEdge ? Math.nextDown(1.0D) : 0.0D;
        }
        @Override public double nextGaussian() { return 0.0D; }
    }
}
