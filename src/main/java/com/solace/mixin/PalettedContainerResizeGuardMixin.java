package com.solace.mixin;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Patch for recursive PalettedContainer resize loops.
 *
 * Original crash pattern:
 *
 *   java.lang.StackOverflowError: Ticking block entity
 *     at net.minecraft.world.level.chunk.PalettedContainer.m_7248_
 *     at net.minecraft.world.level.chunk.LinearPalette.m_6796_
 *     at net.minecraft.world.level.chunk.PalettedContainer.m_7248_
 *     at net.minecraft.world.level.chunk.LinearPalette.m_6796_
 *     ...
 *
 * This guard avoids inspecting PalettedContainer internals. The previous
 * reflection-based approach was fragile in modded environments because the
 * internal object may be PalettedContainer$Configuration rather than Palette.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerResizeGuardMixin<T> {

    /**
     * Per-thread set of PalettedContainer instances currently inside onResize.
     *
     * Identity semantics are intentional: we only want to block recursive resize
     * on the exact same container instance, not every nested palette resize.
     */
    @Unique
    private static final ThreadLocal<Set<PalettedContainer<?>>> solace$resizingContainers =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    @Inject(
            method = "onResize",
            at = @At("HEAD"),
            cancellable = true
    )
    private void solace$guardRecursiveResize(
            int requestedBits,
            T objectAdded,
            CallbackInfoReturnable<Integer> cir
    ) {
        PalettedContainer<?> self = (PalettedContainer<?>) (Object) this;
        Set<PalettedContainer<?>> resizing = solace$resizingContainers.get();

        /*
         * If the same PalettedContainer re-enters onResize while it is already
         * resizing, we are in the exact recursion pattern that caused the
         * StackOverflowError.
         *
         * Returning 0 is a fail-soft fallback: it breaks the recursive call chain
         * instead of crashing the server. In palette terms, id 0 is normally the
         * first/default entry. This is intentionally conservative and avoids
         * reflection into PalettedContainer internals.
         */
        if (!resizing.add(self)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(
            method = "onResize",
            at = @At("RETURN")
    )
    private void solace$clearRecursiveResizeGuard(
            int requestedBits,
            T objectAdded,
            CallbackInfoReturnable<Integer> cir
    ) {
        PalettedContainer<?> self = (PalettedContainer<?>) (Object) this;
        Set<PalettedContainer<?>> resizing = solace$resizingContainers.get();

        resizing.remove(self);

        if (resizing.isEmpty()) {
            solace$resizingContainers.remove();
        }
    }
}