package com.solace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Subtle Effects checks the biome of random nearby blocks for biome particles and fireflies every
 * client tick. {@code Level.getBiome} runs vanilla's border-smoothing lookup (eight hashed corner
 * samples per call); JFR put it at about 8% of the render thread with these two callers. For
 * ambient particles the stored 4x4x4 biome cell is accurate enough: effects near a biome border may
 * pick the neighbouring biome a couple of blocks earlier or later.
 */
@Pseudo
@Mixin(targets = {
        "einstein.subtle_effects.ticking.BiomeEffectsManager",
        "einstein.subtle_effects.ticking.FireflyManager"
}, remap = false)
public abstract class SubtleEffectsBiomeLookupMixin {
    @Redirect(method = "tick", require = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_204166_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private static Holder<Biome> solace$cellBiome(Level level, BlockPos pos) {
        return level.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
    }
}
