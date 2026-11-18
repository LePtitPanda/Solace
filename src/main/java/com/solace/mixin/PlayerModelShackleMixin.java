package com.solace.mixin;

import com.solace.client.ShacklesClient;
import com.solace.world.Shackles;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A shackled player holds both hands together in front, whatever else the arms were doing. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelShackleMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void solace$shackled(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw,
                                 float headPitch, CallbackInfo ci) {
        if (entity instanceof Player player && Shackles.on(player)) {
            ShacklesClient.pose((PlayerModel<?>) (Object) this, false);
        }
    }
}
