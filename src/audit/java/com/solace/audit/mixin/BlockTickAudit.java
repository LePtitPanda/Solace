package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.solace.audit.AuditSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class BlockTickAudit {
    @WrapOperation(method = "tickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void audit$block(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        try (AuditSession.Ticket ticket = AuditSession.open("scheduled_block_tick")) {
            if (ticket != null) ticket.begin(BuiltInRegistries.BLOCK.getKey(state.getBlock()), state.getBlock().getClass(), level.dimension().location(), pos);
            original.call(state, level, pos, random);
        }
    }
    @WrapOperation(method = "tickFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void audit$fluid(FluidState state, Level level, BlockPos pos, Operation<Void> original) {
        try (AuditSession.Ticket ticket = AuditSession.open("scheduled_fluid_tick")) {
            if (ticket != null) ticket.begin(BuiltInRegistries.FLUID.getKey(state.getType()), state.getType().getClass(), level.dimension().location(), pos);
            original.call(state, level, pos);
        }
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void audit$randomBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        try (AuditSession.Ticket ticket = AuditSession.open("random_block_tick")) {
            if (ticket != null) ticket.begin(BuiltInRegistries.BLOCK.getKey(state.getBlock()), state.getBlock().getClass(), level.dimension().location(), pos);
            original.call(state, level, pos, random);
        }
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void audit$randomFluid(FluidState state, Level level, BlockPos pos, RandomSource random, Operation<Void> original) {
        try (AuditSession.Ticket ticket = AuditSession.open("random_fluid_tick")) {
            if (ticket != null) ticket.begin(BuiltInRegistries.FLUID.getKey(state.getType()), state.getType().getClass(), level.dimension().location(), pos);
            original.call(state, level, pos, random);
        }
    }
}
