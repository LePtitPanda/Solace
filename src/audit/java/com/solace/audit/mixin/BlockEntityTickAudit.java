package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.solace.audit.AuditSession;
import com.solace.audit.ContentHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityTickAudit {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void audit$tick(BlockEntityTicker<BlockEntity> ticker, Level level, BlockPos pos, BlockState state, BlockEntity entity, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.blockEntity(level.isClientSide ? "client_block_entity_tick" : "server_block_entity_tick", entity, ticker.getClass())) {
            original.call(ticker, level, pos, state, entity);
        }
    }
}
