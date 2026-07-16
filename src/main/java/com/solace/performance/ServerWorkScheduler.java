package com.solace.performance;

import com.solace.config.PerformanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraftforge.event.TickEvent;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * Coordinates small pieces of work which are safe to delay on an integrated
 * server. This class does not move world access onto worker threads.
 */
public final class ServerWorkScheduler {
    private static final double TICK_TIME_SMOOTHING = 0.25D;

    private static final Map<PoiManager, ServerLevel> LEVELS_BY_POI_MANAGER = new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, ServerState> SERVER_STATES = new ConcurrentHashMap<>();

    private ServerWorkScheduler() {
    }

    /** Called from Forge's server tick event for both START and END phases. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        ServerState state = SERVER_STATES.computeIfAbsent(server, ignored -> new ServerState());

        if (event.phase == TickEvent.Phase.START) {
            state.poiCallsThisTick.clear();
            state.tickStartNanos = System.nanoTime();
            state.tickActive = true;
            return;
        }

        long started = state.tickStartNanos;
        state.tickActive = false;
        if (started == 0L) {
            return;
        }

        double elapsedMillis = Math.max(0L, System.nanoTime() - started) / 1_000_000.0D;
        state.lastTickMillis = elapsedMillis;
        if (state.smoothedTickMillis == 0.0D) {
            state.smoothedTickMillis = elapsedMillis;
        } else {
            state.smoothedTickMillis += (elapsedMillis - state.smoothedTickMillis) * TICK_TIME_SMOOTHING;
        }
        state.pressured = state.smoothedTickMillis >= PerformanceConfig.poiPressureThresholdMillis();
    }

    public static void registerLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        LEVELS_BY_POI_MANAGER.put(level.getPoiManager(), level);
        SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new ServerState());
    }

    public static void unregisterLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        PoiManager poiManager = level.getPoiManager();
        LEVELS_BY_POI_MANAGER.remove(poiManager, level);
        ServerState state = SERVER_STATES.get(level.getServer());
        if (state != null) {
            state.poiCallsThisTick.remove(poiManager);
        }
    }

    public static void reset(MinecraftServer server) {
        if (server == null) {
            LEVELS_BY_POI_MANAGER.clear();
            SERVER_STATES.clear();
            return;
        }

        LEVELS_BY_POI_MANAGER.entrySet().removeIf(entry -> entry.getValue().getServer() == server);
        SERVER_STATES.remove(server);
    }

    /**
     * Claims one sorted POI query from the current tick's adaptive budget.
     * Returning false deliberately presents an empty result to the caller; AI
     * callers naturally retry their lookup on a later tick.
     */
    public static boolean allowPoiQuery(PoiManager poiManager) {
        ServerLevel level = LEVELS_BY_POI_MANAGER.get(poiManager);
        if (level == null) {
            return true;
        }

        MinecraftServer server = level.getServer();
        if (!PerformanceConfig.isEnabledFor(server)) {
            return true;
        }

        ServerState state = SERVER_STATES.computeIfAbsent(server, ignored -> new ServerState());
        // Startup, shutdown and asynchronous world work are not safe places to
        // impose a per-tick budget because there may be no following tick retry.
        if (!state.tickActive) {
            return true;
        }

        int budget = state.pressured
                ? PerformanceConfig.pressuredPoiQueriesPerTick()
                : PerformanceConfig.healthyPoiQueriesPerTick();
        int used = state.poiCallsThisTick
                .computeIfAbsent(poiManager, ignored -> new AtomicInteger())
                .incrementAndGet();

        if (used <= budget) {
            state.poiQueriesAllowed.increment();
            return true;
        }

        state.poiQueriesDeferred.increment();
        return false;
    }

    public static Predicate<BlockPos> wrapPoiPositionPredicate(
            PoiManager poiManager,
            Predicate<BlockPos> original
    ) {
        if (original == null || !PerformanceConfig.shouldSkipUnloadedPoiCandidates()) {
            return original;
        }

        ServerLevel level = LEVELS_BY_POI_MANAGER.get(poiManager);
        if (level == null || !PerformanceConfig.isEnabledFor(level.getServer())) {
            return original;
        }

        ServerState state = SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new ServerState());
        return position -> {
            int chunkX = SectionPos.blockToSectionCoord(position.getX());
            int chunkZ = SectionPos.blockToSectionCoord(position.getZ());
            if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                state.unloadedPoiCandidatesSkipped.increment();
                return false;
            }
            return original.test(position);
        };
    }

    public static int capPoiRadius(PoiManager poiManager, int requestedRadius) {
        ServerLevel level = LEVELS_BY_POI_MANAGER.get(poiManager);
        if (level == null || !PerformanceConfig.isEnabledFor(level.getServer())) {
            return requestedRadius;
        }

        int cappedRadius = Math.min(requestedRadius, PerformanceConfig.maxPoiSearchRadius());
        if (cappedRadius != requestedRadius) {
            SERVER_STATES.computeIfAbsent(level.getServer(), ignored -> new ServerState())
                    .poiRadiiCapped.increment();
        }
        return cappedRadius;
    }

    /**
     * Lets optional background jobs yield while the integrated server is
     * already missing its tick-time target.
     */
    public static boolean isPressured(MinecraftServer server) {
        if (server == null || !PerformanceConfig.isEnabledFor(server)) {
            return false;
        }
        ServerState state = SERVER_STATES.get(server);
        return state != null && state.pressured;
    }

    /**
     * Coalesces repeated integrated-server saves while the pause screen is open.
     * The first request is allowed immediately; subsequent requests are allowed
     * after the configured interval.
     */
    public static boolean shouldRunPauseSave(MinecraftServer server) {
        if (server == null || !PerformanceConfig.isPauseSaveCoalescingEnabled(server)) {
            return true;
        }

        ServerState state = SERVER_STATES.computeIfAbsent(server, ignored -> new ServerState());
        long now = System.nanoTime();
        long interval = PerformanceConfig.pauseSaveIntervalNanos();

        while (true) {
            long last = state.lastPauseSaveNanos.get();
            if (last != 0L && now - last < interval) {
                state.pauseSavesCoalesced.increment();
                return false;
            }
            if (state.lastPauseSaveNanos.compareAndSet(last, now)) {
                state.pauseSavesAllowed.increment();
                return true;
            }
        }
    }

    public static String describeStatus(MinecraftServer server) {
        if (server == null) {
            return "Solace performance scheduler: no server";
        }

        ServerState state = SERVER_STATES.get(server);
        int registeredLevels = 0;
        for (ServerLevel level : LEVELS_BY_POI_MANAGER.values()) {
            if (level.getServer() == server) {
                registeredLevels++;
            }
        }

        if (state == null) {
            return String.format(
                    Locale.ROOT,
                    "Solace performance scheduler: enabled=%s, registeredLevels=%d, no tick samples",
                    PerformanceConfig.isEnabledFor(server),
                    registeredLevels
            );
        }

        int budget = state.pressured
                ? PerformanceConfig.pressuredPoiQueriesPerTick()
                : PerformanceConfig.healthyPoiQueriesPerTick();
        int usedThisTick = state.poiCallsThisTick.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        return String.format(
                Locale.ROOT,
                "Solace performance scheduler: enabled=%s, levels=%d, tick=%.2f ms (avg %.2f, %s), "
                        + "POI=%d/%d this tick, allowed=%d, deferred=%d, radiusCaps=%d, unloadedSkipped=%d, "
                        + "pauseSaves=%d allowed/%d coalesced",
                PerformanceConfig.isEnabledFor(server),
                registeredLevels,
                state.lastTickMillis,
                state.smoothedTickMillis,
                state.pressured ? "pressured" : "healthy",
                usedThisTick,
                budget,
                state.poiQueriesAllowed.sum(),
                state.poiQueriesDeferred.sum(),
                state.poiRadiiCapped.sum(),
                state.unloadedPoiCandidatesSkipped.sum(),
                state.pauseSavesAllowed.sum(),
                state.pauseSavesCoalesced.sum()
        );
    }

    private static final class ServerState {
        private final Map<PoiManager, AtomicInteger> poiCallsThisTick = new ConcurrentHashMap<>();
        private final AtomicLong lastPauseSaveNanos = new AtomicLong();
        private final LongAdder poiQueriesAllowed = new LongAdder();
        private final LongAdder poiQueriesDeferred = new LongAdder();
        private final LongAdder poiRadiiCapped = new LongAdder();
        private final LongAdder unloadedPoiCandidatesSkipped = new LongAdder();
        private final LongAdder pauseSavesAllowed = new LongAdder();
        private final LongAdder pauseSavesCoalesced = new LongAdder();

        private volatile boolean tickActive;
        private volatile boolean pressured;
        private volatile long tickStartNanos;
        private volatile double lastTickMillis;
        private volatile double smoothedTickMillis;
    }
}
