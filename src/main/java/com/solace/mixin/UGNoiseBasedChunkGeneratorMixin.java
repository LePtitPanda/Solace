package com.solace.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Adds RU-specific surface rules when The Undergarden's custom noise generator is built.
 *
 * The biome source itself is patched by Solace's data override for
 * data/undergarden/dimension/undergarden.json. This mixin fixes the part a pure data
 * biome-source patch cannot handle cleanly: RU Nether plants/trees expect RU/Nether
 * substrates, not Undergarden deepturf/depthrock.
 */
@Mixin(targets = "quek.undergarden.world.gen.UGNoiseBasedChunkGenerator", remap = false)
public abstract class UGNoiseBasedChunkGeneratorMixin {
    private static final ResourceLocation UNDERGARDEN_DEPTHROCK = new ResourceLocation("undergarden", "depthrock");

    private static final ResourceKey<Biome> GLISTERING_MEADOW = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation("regions_unexplored", "glistering_meadow")
    );

    private static final ResourceKey<Biome> BLACKSTONE_BASIN = ResourceKey.create(
            Registries.BIOME,
            new ResourceLocation("regions_unexplored", "blackstone_basin")
    );

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;<init>(Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/core/Holder;)V",
                    remap = true
            ),
            index = 1,
            remap = true
    )
    private Holder<NoiseGeneratorSettings> solace$patchUndergardenSurfaceRules(Holder<NoiseGeneratorSettings> originalHolder) {
        NoiseGeneratorSettings original = originalHolder.value();

        ResourceLocation defaultBlockId = BuiltInRegistries.BLOCK.getKey(original.defaultBlock().getBlock());
        if (!UNDERGARDEN_DEPTHROCK.equals(defaultBlockId)) {
            return originalHolder;
        }

        SurfaceRules.RuleSource patchedSurfaceRule = SurfaceRules.sequence(
                solace$ruNetherSurfaceRules(),
                original.surfaceRule()
        );

        NoiseGeneratorSettings patched = new NoiseGeneratorSettings(
                original.noiseSettings(),
                original.defaultBlock(),
                original.defaultFluid(),
                original.noiseRouter(),
                patchedSurfaceRule,
                original.spawnTarget(),
                original.seaLevel(),
                original.disableMobGeneration(),
                original.aquifersEnabled(),
                original.oreVeinsEnabled(),
                original.useLegacyRandomSource()
        );

        return Holder.direct(patched);
    }

    private static SurfaceRules.RuleSource solace$ruNetherSurfaceRules() {
        SurfaceRules.RuleSource glisteringNylium = solace$block("regions_unexplored", "glistering_nylium", Blocks.NETHERRACK);
        SurfaceRules.RuleSource glisteringWart = solace$block("regions_unexplored", "glistering_wart", Blocks.NETHERRACK);
        SurfaceRules.RuleSource cobaltNylium = solace$block("regions_unexplored", "cobalt_nylium", Blocks.BLACKSTONE);
        SurfaceRules.RuleSource netherrack = SurfaceRules.state(Blocks.NETHERRACK.defaultBlockState());
        SurfaceRules.RuleSource blackstone = SurfaceRules.state(Blocks.BLACKSTONE.defaultBlockState());

        SurfaceRules.RuleSource glisteringSurface = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.NETHER_WART, 1.17D), glisteringWart),
                                glisteringNylium
                        )
                ),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, netherrack),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, netherrack)
        );

        SurfaceRules.RuleSource blackstoneSurface = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, 0.0D), cobaltNylium),
                                blackstone
                        )
                ),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, blackstone),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, blackstone)
        );

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.isBiome(GLISTERING_MEADOW), glisteringSurface),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(BLACKSTONE_BASIN), blackstoneSurface)
        );
    }

    private static SurfaceRules.RuleSource solace$block(String namespace, String path, Block fallback) {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(namespace, path));
        if (block == Blocks.AIR) {
            block = fallback;
        }
        return SurfaceRules.state(block.defaultBlockState());
    }
}
