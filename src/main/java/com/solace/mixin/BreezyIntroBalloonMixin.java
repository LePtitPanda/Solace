package com.solace.mixin;

import com.solace.world.BalloonIntroManager;
import codyhuh.breezy.common.entity.HotAirBalloonEntity;
import codyhuh.breezy.common.network.NewWindSavedData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HotAirBalloonEntity.class)
public abstract class BreezyIntroBalloonMixin {

    @Inject(method = "getTargetDirection", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$guideIntroBalloon(NewWindSavedData data, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;

        if (!entity.getPersistentData().getBoolean(BalloonIntroManager.INTRO_BALLOON_KEY)) {
            return;
        }

        double targetX = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_X_KEY);
        double targetY = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_Y_KEY);
        double targetZ = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_Z_KEY);

        Vec3 target = new Vec3(targetX, targetY, targetZ);
        Vec3 direction = target.subtract(entity.position());

        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalDistance < 36.0D) {
            cir.setReturnValue(new Vec3(0.0D, -0.16D, 0.0D));
            return;
        }

        Vec3 normalized = new Vec3(direction.x, 0.0D, direction.z).normalize();
        double speed = horizontalDistance < 140.0D ? 0.32D : 0.55D;

        cir.setReturnValue(new Vec3(normalized.x * speed, -0.11D, normalized.z * speed));
    }
}
