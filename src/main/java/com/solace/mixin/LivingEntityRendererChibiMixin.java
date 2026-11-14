package com.solace.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solace.client.quests.ChibiPortrait;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the dialogue portrait reshape the model after its animation pose, for one render only. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererChibiMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void solace$chibiPose(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                  int light, CallbackInfo ci) {
        ChibiPortrait.reshape(model);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void solace$chibiRestore(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                     int light, CallbackInfo ci) {
        ChibiPortrait.restore();
    }
}
