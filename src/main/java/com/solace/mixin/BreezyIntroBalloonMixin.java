package com.solace.mixin;

import com.solace.world.BalloonIntroManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "codyhuh.breezy.common.entity.HotAirBalloonEntity", remap = false)
public abstract class BreezyIntroBalloonMixin {

    @Inject(method = "getTargetDirection", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$getIntroTargetDirection(Object windData, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;

        if (!entity.getPersistentData().getBoolean(BalloonIntroManager.INTRO_BALLOON_KEY)) {
            return;
        }

        double targetX = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_X_KEY);
        double targetY = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_Y_KEY);
        double targetZ = entity.getPersistentData().getDouble(BalloonIntroManager.TARGET_Z_KEY);

        Vec3 delta = new Vec3(targetX - entity.getX(), targetY - entity.getY(), targetZ - entity.getZ());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        if (horizontalDistance < 36.0D) {
            cir.setReturnValue(new Vec3(0.0D, -0.18D, 0.0D));
            return;
        }

        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z).normalize();
        double speed = horizontalDistance < 140.0D ? 0.45D : 0.8D;
        double descent = entity.getY() > targetY + 18.0D ? -0.16D : -0.06D;

        cir.setReturnValue(new Vec3(horizontal.x * speed, descent, horizontal.z * speed));
    }
}
