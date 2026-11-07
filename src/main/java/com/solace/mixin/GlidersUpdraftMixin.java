package com.solace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.venturecraft.gliders.util.VCGliderTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.venturecraft.gliders.util.ClientUtil", remap = false)
public abstract class GlidersUpdraftMixin {
    @Inject(method = "updraftParticles", at = @At("HEAD"), cancellable = true, remap = false)
    private static void solace$skipIneligibleBlocks(BlockState state, Level world, BlockPos pos,
                                                    RandomSource random, CallbackInfo ci) {
        if (!state.is(VCGliderTags.UPDRAFT_BLOCKS)) ci.cancel();
    }
}
