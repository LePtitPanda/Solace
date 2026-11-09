package com.solace.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderSortTask;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Measurement only (-Dsolace.schedulerStats=true): how many translucent re-sort jobs run and how
 * much worker time they take, to decide whether a BSP sort rewrite would pay off.
 */
@Mixin(value = ChunkBuilderSortTask.class, remap = false)
public abstract class EmbeddiumSortStatsMixin {
    @Unique private static final Logger solace$LOGGER = LogManager.getLogger("Solace/SortStats");
    @Unique private static final ThreadLocal<long[]> solace$START = ThreadLocal.withInitial(() -> new long[1]);
    @Unique private static final AtomicLong solace$jobs = new AtomicLong();
    @Unique private static final AtomicLong solace$nanos = new AtomicLong();
    @Unique private static final AtomicLong solace$window = new AtomicLong();

    @Inject(method = "execute", at = @At("HEAD"))
    private void solace$begin(ChunkBuildContext context, CancellationToken token, CallbackInfoReturnable<ChunkBuildOutput> cir) {
        solace$START.get()[0] = System.nanoTime();
    }

    @Inject(method = "execute", at = @At("RETURN"))
    private void solace$end(ChunkBuildContext context, CancellationToken token, CallbackInfoReturnable<ChunkBuildOutput> cir) {
        long now = System.nanoTime();
        solace$jobs.incrementAndGet();
        solace$nanos.addAndGet(now - solace$START.get()[0]);
        long start = solace$window.get();
        if (start == 0L) {
            solace$window.compareAndSet(0L, now);
        } else if (now - start >= 10_000_000_000L && solace$window.compareAndSet(start, now)) {
            long jobs = solace$jobs.getAndSet(0L);
            long nanos = solace$nanos.getAndSet(0L);
            double seconds = (now - start) / 1.0e9;
            solace$LOGGER.info("sortJobs/s={} workerMs/s={} avgUs={}",
                    Math.round(jobs / seconds),
                    String.format(java.util.Locale.ROOT, "%.2f", nanos / 1.0e6 / seconds),
                    jobs == 0 ? 0 : nanos / 1000 / jobs);
        }
    }
}
