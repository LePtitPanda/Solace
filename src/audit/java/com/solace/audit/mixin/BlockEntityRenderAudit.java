package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solace.audit.AuditSession;
import com.solace.audit.ContentHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderAudit {
    @WrapOperation(method = "setupAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static void audit$render(BlockEntityRenderer<BlockEntity> renderer, BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.blockEntity("block_entity_render_cpu", entity, renderer.getClass())) {
            original.call(renderer, entity, partialTick, pose, buffers, light, overlay);
        }
    }
}
