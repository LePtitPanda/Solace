package com.solace.mixin;

import com.google.gson.JsonObject;
import com.solace.world.NaturalSurfaceSpawnSupport;
import mcjty.incontrol.spawner.PositionCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Astralis surface-hostile pools beneath natural canopy without treating
 * elevated structures as permanent night spawning platforms.
 */
@Mixin(value = PositionCheck.class, remap = false)
public abstract class InControlPositionCheckMixin {
    @Inject(method = "parse", at = @At("TAIL"))
    private static void solace$requireNaturalSurfaceForHostileNightPool(
            JsonObject conditions,
            PositionCheck.Builder builder,
            CallbackInfo callbackInfo
    ) {
        if (!isSurfaceHostileConditionGroup(conditions)) {
            return;
        }

        builder.extraCondition((level, pos, player) -> NaturalSurfaceSpawnSupport.hasNaturalSkyAccess(level, pos));
    }

    private static boolean isSurfaceHostileConditionGroup(JsonObject conditions) {
        return conditions.has("mintime")
                && conditions.has("maxtime")
                && conditions.has("maxlight_full")
                && conditions.has("minlight_sky");
    }
}
