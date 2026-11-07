package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solace.audit.AuditSession;
import com.solace.audit.ContentHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleEngine.class)
public abstract class ParticleAudit {
    @WrapMethod(method = "tickParticle")
    private void audit$tick(Particle particle, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.implementation("particle_tick", particle)) { original.call(particle); }
    }
    // Renderer replacements can bypass this vanilla loop. Absence of rows is not evidence of zero particle cost.
    // Forge's six-argument render overload keeps its name in SRG; the vanilla five-argument one only delegates.
    @WrapOperation(method = "render", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;m_5744_(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V", remap = false), require = 0)
    private void audit$render(Particle particle, VertexConsumer vertices, Camera camera, float partialTick, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.implementation("particle_render_cpu", particle)) { original.call(particle, vertices, camera, partialTick); }
    }
    @WrapMethod(method = "render", remap = false)
    private void audit$batch(PoseStack pose, MultiBufferSource.BufferSource buffers, LightTexture light, Camera camera,
            float partialTick, Frustum frustum, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.implementation("particle_batch_render_cpu", this)) {
            original.call(pose, buffers, light, camera, partialTick, frustum);
        }
    }
}
