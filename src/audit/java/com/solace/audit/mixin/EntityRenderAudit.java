package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solace.audit.AuditSession;
import com.solace.audit.ContentHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderAudit {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void audit$render(EntityRenderer<Entity> renderer, Entity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.entity("entity_render_cpu", entity, renderer.getClass())) {
            original.call(renderer, entity, yaw, partialTick, pose, buffers, light);
        }
    }
}
