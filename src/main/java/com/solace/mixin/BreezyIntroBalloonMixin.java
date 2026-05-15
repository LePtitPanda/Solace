package com.solace.mixin;

import codyhuh.breezy.common.entity.HotAirBalloonEntity;
import codyhuh.breezy.common.network.NewWindSavedData;
import com.solace.world.BalloonIntroManager;
import com.solace.world.SolaceIntroBalloonAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "codyhuh.breezy.common.entity.HotAirBalloonEntity", remap = false)
public abstract class BreezyIntroBalloonMixin implements SolaceIntroBalloonAccess {
    @Unique
    private static final EntityDataAccessor<Boolean> SOLACE$INTRO_BALLOON = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$TARGET_X = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$TARGET_Y = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$TARGET_Z = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);

    @Inject(method = {"defineSynchedData", "m_8097_"}, at = @At("TAIL"), remap = false, require = 0)
    private void solace$defineIntroSyncedData(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        entity.getEntityData().define(SOLACE$INTRO_BALLOON, false);
        entity.getEntityData().define(SOLACE$TARGET_X, 0.0F);
        entity.getEntityData().define(SOLACE$TARGET_Y, 0.0F);
        entity.getEntityData().define(SOLACE$TARGET_Z, 0.0F);
    }

    @Override
    public void solace$setIntroTarget(double x, double y, double z) {
        Entity entity = (Entity) (Object) this;
        entity.getEntityData().set(SOLACE$INTRO_BALLOON, true);
        entity.getEntityData().set(SOLACE$TARGET_X, (float) x);
        entity.getEntityData().set(SOLACE$TARGET_Y, (float) y);
        entity.getEntityData().set(SOLACE$TARGET_Z, (float) z);

        entity.getPersistentData().putBoolean(BalloonIntroManager.INTRO_BALLOON_KEY, true);
        entity.getPersistentData().putDouble(BalloonIntroManager.TARGET_X_KEY, x);
        entity.getPersistentData().putDouble(BalloonIntroManager.TARGET_Y_KEY, y);
        entity.getPersistentData().putDouble(BalloonIntroManager.TARGET_Z_KEY, z);
    }

    @Override
    public boolean solace$isIntroBalloon() {
        Entity entity = (Entity) (Object) this;
        return entity.getEntityData().get(SOLACE$INTRO_BALLOON);
    }

    @Inject(method = "getTargetDirection", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$getIntroTargetDirection(NewWindSavedData windData, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;

        if (!entity.getEntityData().get(SOLACE$INTRO_BALLOON)) {
            return;
        }

        double targetX = entity.getEntityData().get(SOLACE$TARGET_X);
        double targetY = entity.getEntityData().get(SOLACE$TARGET_Y);
        double targetZ = entity.getEntityData().get(SOLACE$TARGET_Z);

        Vec3 delta = new Vec3(targetX - entity.getX(), targetY - entity.getY(), targetZ - entity.getZ());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        if (horizontalDistance < 36.0D) {
            cir.setReturnValue(new Vec3(0.0D, -0.08D, 0.0D));
            return;
        }

        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z).normalize();
        double speed = horizontalDistance < 160.0D ? 0.08D : 0.16D;
        double descent = entity.getY() > targetY + 18.0D ? -0.055D : -0.015D;

        cir.setReturnValue(new Vec3(horizontal.x * speed, descent, horizontal.z * speed));
    }
}
