package com.solace.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Developer measurement only, applied when launched with -Dsolace.graphStats=true: times Embeddium's
 * visibility graph search ({@code RenderSectionManager.update}) and logs its cost per frame, to decide
 * whether moving it off the render thread is worth doing.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class EmbeddiumGraphStatsMixin {
    @Unique private static final Logger solace$LOGGER = LogManager.getLogger("Solace/GraphStats");
    @Unique private static final int solace$WINDOW = 2000;

    @Unique private long solace$start;
    @Unique private final long[] solace$samples = new long[solace$WINDOW];
    @Unique private int solace$count;
    @Unique private int solace$frames;

    @Inject(method = "update", at = @At("HEAD"))
    private void solace$graphStart(CallbackInfo ci) {
        this.solace$start = System.nanoTime();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void solace$graphEnd(CallbackInfo ci) {
        this.solace$samples[this.solace$count++] = System.nanoTime() - this.solace$start;
        if (this.solace$count == solace$WINDOW) this.solace$flush();
    }

    /** tickVisibleRenders runs once per rendered frame; used as the frame counter. */
    @Inject(method = "tickVisibleRenders", at = @At("HEAD"))
    private void solace$frame(CallbackInfo ci) {
        this.solace$frames++;
    }

    @Unique
    private void solace$flush() {
        long[] sorted = Arrays.copyOf(this.solace$samples, this.solace$count);
        Arrays.sort(sorted);
        long total = 0L;
        for (long v : sorted) total += v;
        solace$LOGGER.info("graph search: {} searches in {} frames, avg={} ms p50={} ms p95={} ms p99={} ms max={} ms, per-frame avg={} ms",
                sorted.length, this.solace$frames,
                solace$ms(total / sorted.length), solace$ms(sorted[sorted.length / 2]),
                solace$ms(sorted[(int) (sorted.length * 0.95)]), solace$ms(sorted[(int) (sorted.length * 0.99)]),
                solace$ms(sorted[sorted.length - 1]),
                solace$ms(this.solace$frames == 0 ? 0 : total / this.solace$frames));
        this.solace$count = 0;
        this.solace$frames = 0;
    }

    @Unique
    private static String solace$ms(long nanos) {
        return String.format("%.3f", nanos / 1e6);
    }
}
