package com.solace.mixin;

import com.solace.compat.performance.ClientPerformancePatches;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium emits one multidraw command per visible face direction of every section (up to seven).
 * A section's face slices sit back to back in the region vertex buffer, in {@link ModelQuadFacing}
 * order, and unsorted passes index them with the shared quad index pattern. So a run of adjacent
 * visible slices is one contiguous quad range and can be drawn as a single command. Empty slices
 * hold no vertices, so they never break a run; only a slice culled for facing away does.
 * Sorted (translucent) passes use per-section index buffers and keep Embeddium's emission.
 *
 * Idea credited to Celeritas (embeddedt, "Aggressively optimize multidraw command emission");
 * this is an independent implementation for Embeddium 0.3.x on 1.20.1. Launch with
 * -Dsolace.drawStats=true to log draw commands and submit time, with merging on or off.
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class EmbeddiumMergedDrawsMixin {
    @Unique private static final boolean solace$MERGE = ClientPerformancePatches.mergedDrawCommands();
    @Unique private static final boolean solace$STATS = Boolean.getBoolean("solace.drawStats");
    @Unique private static final Logger solace$LOGGER = LogManager.getLogger("Solace/MergedDraws");

    @Unique private static long solace$commands;
    @Unique private static long solace$submits;
    @Unique private static long solace$submitNanos;
    @Unique private static long solace$windowStart;
    @Unique private static long solace$submitStart;

    @Inject(method = "addDrawCommands", at = @At("HEAD"), cancellable = true, require = 1)
    private static void solace$mergeFacingRuns(MultiDrawBatch batch, long pMeshData, int mask,
                                               long indexPointerMask, CallbackInfo ci) {
        if (!solace$MERGE || indexPointerMask != 0L) {
            return;
        }

        long pBaseVertex = batch.pBaseVertex;
        long pElementCount = batch.pElementCount;
        long pElementPointer = batch.pElementPointer;
        int size = batch.size;

        int runStart = -1;
        int runElements = 0;

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            int elements = SectionRenderDataUnsafe.getElementCount(pMeshData, facing);
            if (((mask >> facing) & 1) != 0) {
                if (runStart < 0) {
                    runStart = facing;
                    runElements = 0;
                }
                runElements += elements;
            } else if (elements != 0 && runStart >= 0) {
                // A culled slice with geometry ends the run.
                solace$put(pBaseVertex, pElementCount, pElementPointer, size++, pMeshData, runStart, runElements);
                runStart = -1;
            }
        }

        if (runStart >= 0) {
            solace$put(pBaseVertex, pElementCount, pElementPointer, size++, pMeshData, runStart, runElements);
        }

        batch.size = size;
        ci.cancel();
    }

    @Unique
    private static void solace$put(long pBaseVertex, long pElementCount, long pElementPointer, int slot,
                                   long pMeshData, int facing, int elements) {
        MemoryUtil.memPutInt(pBaseVertex + ((long) slot << 2), (int) SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing));
        MemoryUtil.memPutInt(pElementCount + ((long) slot << 2), elements);
        MemoryUtil.memPutAddress(pElementPointer + ((long) slot << 3), 0L);
    }

    @Inject(method = "executeDrawBatch", at = @At("HEAD"), require = 0)
    private static void solace$statsBegin(CallbackInfo ci) {
        if (solace$STATS) {
            solace$submitStart = System.nanoTime();
        }
    }

    @Inject(method = "executeDrawBatch", at = @At("RETURN"), require = 0)
    private static void solace$statsEnd(me.jellysquid.mods.sodium.client.gl.device.CommandList commandList,
                                        me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation tessellation,
                                        MultiDrawBatch batch, CallbackInfo ci) {
        if (!solace$STATS) {
            return;
        }
        long now = System.nanoTime();
        solace$submitNanos += now - solace$submitStart;
        solace$submits++;
        solace$commands += batch.size;

        if (solace$windowStart == 0L) {
            solace$windowStart = now;
        } else if (now - solace$windowStart >= 10_000_000_000L) {
            double seconds = (now - solace$windowStart) / 1.0e9;
            solace$LOGGER.info("merge={} commands/s={} multidraws/s={} submit ms/s={}",
                    solace$MERGE,
                    Math.round(solace$commands / seconds),
                    Math.round(solace$submits / seconds),
                    String.format(java.util.Locale.ROOT, "%.3f", solace$submitNanos / 1.0e6 / seconds));
            solace$commands = 0L;
            solace$submits = 0L;
            solace$submitNanos = 0L;
            solace$windowStart = now;
        }
    }
}
