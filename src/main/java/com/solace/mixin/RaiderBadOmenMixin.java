package com.solace.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.solace.Solace;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gamerule captainsGiveBadOmen (off by default): while off, killing a patrol captain neither gives Bad Omen
 * nor clears the one the player already has. Bad Omen from /effect and quests is untouched.
 */
@Mixin(Raider.class)
public abstract class RaiderBadOmenMixin {
    @WrapWithCondition(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;removeEffectNoUpdate(Lnet/minecraft/world/effect/MobEffect;)Lnet/minecraft/world/effect/MobEffectInstance;"))
    private boolean solace$keepOmen(Player player, MobEffect effect) {
        return player.level().getGameRules().getBoolean(Solace.CAPTAINS_GIVE_BAD_OMEN);
    }

    @WrapWithCondition(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean solace$captainOmen(Player player, MobEffectInstance effect) {
        return player.level().getGameRules().getBoolean(Solace.CAPTAINS_GIVE_BAD_OMEN);
    }
}
