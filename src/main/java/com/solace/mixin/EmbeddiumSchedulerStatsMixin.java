package com.solace.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Measurement only (-Dsolace.schedulerStats=true). Answers two questions about Embeddium's chunk
 * scheduling before any rewrite: do mesh workers run dry between frames while sections still wait
 * (meshing throughput tied to frame rate), and how long does uploading every finished mesh in one
 * frame take (upload spikes)?
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class EmbeddiumSchedulerStatsMixin {
    @Unique private static final Logger solace$LOGGER = LogManager.getLogger("Solace/SchedulerStats");

    @Shadow @Final private ChunkBuilder builder;
    @Shadow private Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists;
    @Shadow @Final private ConcurrentLinkedDeque<ChunkJobResult<ChunkBuildOutput>> buildResults;

    @Unique private long solace$windowStart;
    @Unique private int solace$frames;
    @Unique private int solace$framesWithBacklog;
    @Unique private int solace$framesStarved;
    @Unique private long solace$idleWorkerSlots;
    @Unique private long solace$uploadStart;
    @Unique private int solace$uploadResults;
    @Unique private long solace$totalResults;
    @Unique private int solace$maxResults;
    @Unique private float[] solace$uploadMs = new float[4096];
    @Unique private int solace$uploadCount;

    @Unique private static java.lang.reflect.Method solace$sizeMethod;
    @Unique private static java.lang.reflect.Field solace$queueField;

    /** ChunkBuilder's job queue type is package-private, so read its size reflectively (stats mode only). */
    @Unique
    private static int solace$queueSize(Object builder) {
        try {
            if (solace$queueField == null) {
                solace$queueField = builder.getClass().getDeclaredField("queue");
                solace$queueField.setAccessible(true);
                solace$sizeMethod = solace$queueField.getType().getDeclaredMethod("size");
                solace$sizeMethod.setAccessible(true);
            }
            return (int) solace$sizeMethod.invoke(solace$queueField.get(builder));
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void solace$sampleWorkers(boolean updateImmediately, CallbackInfo ci) {
        int backlog = 0;
        if (this.rebuildLists != null) {
            ArrayDeque<RenderSection> a = this.rebuildLists.get(ChunkUpdateType.REBUILD);
            ArrayDeque<RenderSection> b = this.rebuildLists.get(ChunkUpdateType.INITIAL_BUILD);
            backlog = (a == null ? 0 : a.size()) + (b == null ? 0 : b.size());
        }
        EmbeddiumChunkBuilderAccessor acc = (EmbeddiumChunkBuilderAccessor) this.builder;
        int threads = acc.solace$getThreads().size();
        int busy = acc.solace$getBusyThreadCount().get();
        int queued = solace$queueSize(this.builder);
        this.solace$frames++;
        if (backlog > 0) {
            this.solace$framesWithBacklog++;
            if (queued == 0 && busy < threads) {
                this.solace$framesStarved++;
                this.solace$idleWorkerSlots += threads - busy;
            }
        }
    }

    @Unique private int solace$framesLeftover;
    @Unique private long solace$leftoverSum;

    @Inject(method = "updateChunks", at = @At("RETURN"))
    private void solace$sampleLeftover(boolean updateImmediately, CallbackInfo ci) {
        int left = 0;
        if (this.rebuildLists != null) {
            ArrayDeque<RenderSection> a = this.rebuildLists.get(ChunkUpdateType.REBUILD);
            ArrayDeque<RenderSection> b = this.rebuildLists.get(ChunkUpdateType.INITIAL_BUILD);
            left = (a == null ? 0 : a.size()) + (b == null ? 0 : b.size());
        }
        if (left > 0) {
            this.solace$framesLeftover++;
            this.solace$leftoverSum += left;
        }
    }

    @Inject(method = "uploadChunks", at = @At("HEAD"))
    private void solace$uploadBegin(CallbackInfo ci) {
        this.solace$uploadResults = this.buildResults.size();
        this.solace$uploadStart = System.nanoTime();
    }

    @Inject(method = "uploadChunks", at = @At("RETURN"))
    private void solace$uploadEnd(CallbackInfo ci) {
        long now = System.nanoTime();
        if (this.solace$uploadResults > 0) {
            if (this.solace$uploadCount < this.solace$uploadMs.length) {
                this.solace$uploadMs[this.solace$uploadCount++] = (now - this.solace$uploadStart) / 1.0e6F;
            }
            this.solace$totalResults += this.solace$uploadResults;
            this.solace$maxResults = Math.max(this.solace$maxResults, this.solace$uploadResults);
        }
        if (this.solace$windowStart == 0L) {
            this.solace$windowStart = now;
            return;
        }
        if (now - this.solace$windowStart < 10_000_000_000L) {
            return;
        }
        float[] ms = Arrays.copyOf(this.solace$uploadMs, this.solace$uploadCount);
        Arrays.sort(ms);
        float sum = 0F;
        int over2 = 0, over5 = 0;
        for (float m : ms) {
            sum += m;
            if (m > 2F) over2++;
            if (m > 5F) over5++;
        }
        int n = ms.length;
        int threads = ((EmbeddiumChunkBuilderAccessor) this.builder).solace$getThreads().size();
        solace$LOGGER.info("leftoverFrames={} avgLeftover={} | frames={} backlogFrames={} starvedFrames={} avgIdleWorkers={} threads={} | uploadFrames={} results={} maxResults={} uploadMs avg={} p99={} max={} >2ms={} >5ms={}",
                this.solace$framesLeftover,
                this.solace$framesLeftover == 0 ? 0 : this.solace$leftoverSum / this.solace$framesLeftover,
                this.solace$frames, this.solace$framesWithBacklog, this.solace$framesStarved,
                this.solace$framesStarved == 0 ? "0" : String.format(java.util.Locale.ROOT, "%.1f", (double) this.solace$idleWorkerSlots / this.solace$framesStarved),
                threads, n, this.solace$totalResults, this.solace$maxResults,
                String.format(java.util.Locale.ROOT, "%.2f", n == 0 ? 0F : sum / n),
                String.format(java.util.Locale.ROOT, "%.2f", n == 0 ? 0F : ms[Math.min(n - 1, (int) (n * 0.99F))]),
                String.format(java.util.Locale.ROOT, "%.2f", n == 0 ? 0F : ms[n - 1]),
                over2, over5);
        this.solace$windowStart = now;
        this.solace$frames = 0;
        this.solace$framesLeftover = 0;
        this.solace$leftoverSum = 0L;
        this.solace$framesWithBacklog = 0;
        this.solace$framesStarved = 0;
        this.solace$idleWorkerSlots = 0L;
        this.solace$totalResults = 0L;
        this.solace$maxResults = 0;
        this.solace$uploadCount = 0;
    }
}
