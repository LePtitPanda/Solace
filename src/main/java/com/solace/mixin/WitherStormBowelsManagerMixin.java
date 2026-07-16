package com.solace.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import nonamecrackers2.witherstormmod.common.capability.WitherStormBowelsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Defers Wither Storm's expensive Bowels structure lookup until an arena is
 * actually requested.
 *
 * <p>The mod already falls back to {@code getAvailableStructure()} inside
 * {@code getOrCreateInstanceFor()} when no pre-located structure is cached.
 * Skipping only the eager lookup avoids generating the large Bowels structure
 * during every dimension load while preserving setup for saved instances and
 * the normal on-demand arena path.</p>
 */
@Mixin(value = WitherStormBowelsManager.class, remap = false)
public abstract class WitherStormBowelsManagerMixin {
    @Redirect(
            method = "onLoad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnonamecrackers2/witherstormmod/common/capability/WitherStormBowelsManager;getAvailableStructure()Lcom/mojang/datafixers/util/Pair;",
                    remap = false
            ),
            remap = false
    )
    private Pair<BlockPos, StructureStart> solace$deferBowelsArenaLookup(
            WitherStormBowelsManager manager
    ) {
        return null;
    }
}
