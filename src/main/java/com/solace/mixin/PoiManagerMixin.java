package com.solace.mixin;

import com.mojang.datafixers.util.Pair;
import com.solace.performance.ServerWorkScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(PoiManager.class)
public abstract class PoiManagerMixin {
    @Inject(
            method = "findAllClosestFirstWithType",
            at = @At("HEAD"),
            cancellable = true
    )
    private void solace$applyAdaptiveQueryBudget(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> positionPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Stream<Pair<Holder<PoiType>, BlockPos>>> cir
    ) {
        PoiManager self = (PoiManager) (Object) this;
        if (!ServerWorkScheduler.allowPoiQuery(self)) {
            cir.setReturnValue(Stream.empty());
        }
    }

    @ModifyVariable(
            method = "findAllClosestFirstWithType",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private Predicate<BlockPos> solace$skipUnloadedPoiCandidates(Predicate<BlockPos> original) {
        return ServerWorkScheduler.wrapPoiPositionPredicate((PoiManager) (Object) this, original);
    }

    @ModifyVariable(
            method = "findAllClosestFirstWithType",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int solace$capPoiSearchRadius(int requestedRadius) {
        return ServerWorkScheduler.capPoiRadius((PoiManager) (Object) this, requestedRadius);
    }
}
