package com.solace.mixin.chunkguards;

import com.solace.performance.ChunkLoadGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of Wesley1808's ServerCore optimizations/sync_loads/GroundPathNavigationMixin (MIT).
 * Source: ServerCore 1.20.1, d1d0a02d; see META-INF/licenses/servercore-MIT.txt.
 */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationChunkGuardMixin extends PathNavigation {
    private GroundPathNavigationChunkGuardMixin(Mob mob, Level level) {
        super(mob, level);
    }

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), cancellable = true)
    private void solace$onlyPathfindIfLoaded(BlockPos target, int distance, CallbackInfoReturnable<Path> cir) {
        // The ground navigator reads the target column before constructing its path region.
        if (ChunkLoadGuards.enabled(this.level)
                && ((ServerLevel) this.level).getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            cir.setReturnValue(null);
        }
    }
}
