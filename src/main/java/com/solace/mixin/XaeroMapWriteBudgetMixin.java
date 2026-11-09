package com.solace.mixin;

import com.solace.compat.performance.ClientPerformancePatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Xaero's World Map writes map tiles on the render thread, budgeted at 86,960 ns per elapsed
 * millisecond (8.7% of wall time). With "update chunks" on it re-scans the whole loaded area about
 * every 0.7 s at render distance 16, so it nearly always spends that budget: JFR measured 6-8% of the
 * render thread. At high frame rates the binding limit is instead a re-write rate of 1500 chunks per
 * second (target full pass = chunks * 1000 / 1500 ms, about 0.7 s at render distance 16).
 * This lowers both (defaults 3% and 500 chunks/s, a full pass about every 2.2 s). New chunks reach the
 * map more slowly and edits to mapped terrain take longer to show; the map itself is unchanged.
 * 8.7 and 1500 restore Xaero's values.
 */
@Pseudo
@Mixin(targets = "xaero.map.MapWriter", remap = false)
public abstract class XaeroMapWriteBudgetMixin {
    @ModifyConstant(method = "onRender", constant = @Constant(longValue = 86960L), require = 0, remap = false)
    private long solace$writeBudgetNanosPerMilli(long original) {
        return ClientPerformancePatches.xaeroMapWriteNanosPerMilli();
    }

    @ModifyConstant(method = "onRender", constant = @Constant(intValue = 1500), require = 0, remap = false)
    private int solace$chunksPerSecond(int original) {
        return ClientPerformancePatches.xaeroMapChunksPerSecond();
    }
}
