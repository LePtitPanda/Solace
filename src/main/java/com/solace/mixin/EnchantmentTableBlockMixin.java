package com.solace.mixin;

import com.solace.world.EnchantingReworkSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentTableBlock.class)
public class EnchantmentTableBlockMixin {

    @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
    private void solace$getMenuProvider(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
        cir.setReturnValue(EnchantingReworkSupport.enchantingTableProvider(level, pos));
    }

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void solace$bookshelfBookParticles(BlockState state, Level level, BlockPos pos,
                                                RandomSource random, CallbackInfo ci) {
        EnchantingReworkSupport.spawnEnchantingParticles(level, pos, random);
        ci.cancel();
    }
}
