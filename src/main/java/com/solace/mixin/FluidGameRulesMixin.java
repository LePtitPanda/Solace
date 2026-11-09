package com.solace.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Covers WaterFluid and LavaFluid without replacing their source-conversion hooks. */
@Mixin(GameRules.class)
public abstract class FluidGameRulesMixin {
    @Unique private GameRules.BooleanValue solace$waterSourceConversion;
    @Unique private GameRules.BooleanValue solace$lavaSourceConversion;

    @Redirect(method = "getBoolean", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getRule(Lnet/minecraft/world/level/GameRules$Key;)Lnet/minecraft/world/level/GameRules$Value;"))
    private GameRules.Value<?> solace$cachedFluidRule(GameRules rules, GameRules.Key<GameRules.BooleanValue> key) {
        // Cache each instance's live value, never its boolean. Commands/load/assignFrom mutate it in place;
        // copied GameRules instances start with empty caches and resolve their own independent values.
        if (key == GameRules.RULE_WATER_SOURCE_CONVERSION) {
            if (solace$waterSourceConversion == null) solace$waterSourceConversion = rules.getRule(key);
            return solace$waterSourceConversion;
        }
        if (key == GameRules.RULE_LAVA_SOURCE_CONVERSION) {
            if (solace$lavaSourceConversion == null) solace$lavaSourceConversion = rules.getRule(key);
            return solace$lavaSourceConversion;
        }
        return rules.getRule(key);
    }
}
