package com.solace.mixin;

import com.solace.world.BalloonIntroManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "codyhuh.breezy.common.entity.HotAirBalloonEntity", remap = false)
public abstract class BreezyIntroBalloonMixin {

    @Inject(method = {"travel", "m_7023_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$travelToVillage(Vec3 input, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!entity.getPersistentData().getBoolean(BalloonIntroManager.INTRO_BALLOON_KEY)) {
            return;
        }

        double targetX = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_X_KEY);
        double targetY = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_Y_KEY);
        double targetZ = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_Z_KEY);

        Vec3 delta = new Vec3(targetX - entity.getX(), targetY - entity.getY(), targetZ - entity.getZ());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        Vec3 movement;
        if (horizontalDistance < 36.0D) {
            movement = new Vec3(0.0D, -0.16D, 0.0D);
        } else {
            Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z).normalize();
            double speed = horizontalDistance < 140.0D ? 0.32D : 0.55D;
            movement = new Vec3(horizontal.x * speed, -0.11D, horizontal.z * speed);
        }

        entity.setDeltaMovement(movement);
        entity.move(MoverType.SELF, movement);
        entity.hasImpulse = true;
        ci.cancel();
    }
}
