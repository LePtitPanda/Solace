package com.solace.mixin;

import com.solace.client.performance.DragonBannerIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class BannerIndexChunkMixin {
    @Inject(method = "setBlockEntity", at = @At("RETURN"))
    private void solace$indexBannerEntity(BlockEntity entity, CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel().isClientSide) DragonBannerIndex.changed(chunk, entity.getBlockPos());
    }

    @Inject(method = "removeBlockEntity", at = @At("RETURN"))
    private void solace$removeBannerEntity(BlockPos pos, CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel().isClientSide) DragonBannerIndex.changed(chunk, pos);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void solace$refreshBannerState(BlockPos pos, BlockState state, boolean moving,
                                          CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel().isClientSide) DragonBannerIndex.changed(chunk, pos);
    }
}
