package com.solace.mixin;

import codyhuh.breezy.common.entity.HotAirBalloonEntity;
import codyhuh.breezy.common.network.NewWindSavedData;
import com.solace.world.BalloonIntroManager;
import com.solace.world.SolaceIntroBalloonAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final EntityDataAccessor<Boolean> SOLACE$LANDED = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$TARGET_X = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$TARGET_Y = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$TARGET_Z = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$APPROACH_X = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Float> SOLACE$APPROACH_Z = SynchedEntityData.defineId(HotAirBalloonEntity.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final double SOLACE$Y_MOVEMENT_EPSILON = 0.003D;
    @Unique
    private static final double SOLACE$HORIZONTAL_MOVEMENT_EPSILON_SQUARED = 0.000001D;
    @Unique
    private static final int SOLACE$STATIONARY_Y_TICKS_TO_LAND = 14;
    @Unique
    private double solace$lastX = Double.NaN;
    @Unique
    private double solace$lastY = Double.NaN;
    @Unique
    private double solace$lastZ = Double.NaN;
    @Unique
    private int solace$stationaryYTicks;

    @Inject(method = {"defineSynchedData", "m_8097_"}, at = @At("TAIL"), remap = false, require = 0)
    private void solace$defineIntroSyncedData(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        entity.getEntityData().define(SOLACE$INTRO_BALLOON, false);
        entity.getEntityData().define(SOLACE$LANDED, false);
        entity.getEntityData().define(SOLACE$TARGET_X, 0.0F);
        entity.getEntityData().define(SOLACE$TARGET_Y, 0.0F);
        entity.getEntityData().define(SOLACE$TARGET_Z, 0.0F);
        entity.getEntityData().define(SOLACE$APPROACH_X, 0.0F);
        entity.getEntityData().define(SOLACE$APPROACH_Z, 0.0F);
    }

    @Override
    public void solace$setIntroTarget(double x, double y, double z) {
        Entity entity = (Entity) (Object) this;
        entity.getEntityData().set(SOLACE$INTRO_BALLOON, true);
        entity.getEntityData().set(SOLACE$LANDED, false);
        entity.getEntityData().set(SOLACE$TARGET_X, (float) x);
        entity.getEntityData().set(SOLACE$TARGET_Y, (float) y);
        entity.getEntityData().set(SOLACE$TARGET_Z, (float) z);
        Vec3 approach = new Vec3(x - entity.getX(), 0.0D, z - entity.getZ());
        Vec3 approachDirection = approach.lengthSqr() > 0.000001D ? approach.normalize() : Vec3.ZERO;
        entity.getEntityData().set(SOLACE$APPROACH_X, (float) approachDirection.x);
        entity.getEntityData().set(SOLACE$APPROACH_Z, (float) approachDirection.z);
        entity.setInvulnerable(true);
        this.solace$lastX = Double.NaN;
        this.solace$lastY = Double.NaN;
        this.solace$lastZ = Double.NaN;
        this.solace$stationaryYTicks = 0;

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

    @Override
    public boolean solace$hasLanded() {
        Entity entity = (Entity) (Object) this;
        return entity.getEntityData().get(SOLACE$LANDED);
    }

    @Override
    public void solace$markLanded() {
        Entity entity = (Entity) (Object) this;
        entity.getEntityData().set(SOLACE$LANDED, true);
        entity.setInvulnerable(false);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
    }

    @Override
    public void solace$tickLandingMovementCheck() {
        Entity entity = (Entity) (Object) this;
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        if (Double.isNaN(this.solace$lastY)) {
            this.solace$lastX = x;
            this.solace$lastY = y;
            this.solace$lastZ = z;
            this.solace$stationaryYTicks = 0;
            return;
        }

        if (Math.abs(y - this.solace$lastY) <= SOLACE$Y_MOVEMENT_EPSILON && this.solace$hasLandingSurfaceBelow(entity)) {
            this.solace$stationaryYTicks++;
        } else {
            this.solace$stationaryYTicks = 0;
        }

        this.solace$lastX = x;
        this.solace$lastY = y;
        this.solace$lastZ = z;

        if (this.solace$stationaryYTicks >= SOLACE$STATIONARY_Y_TICKS_TO_LAND) {
            this.solace$markLanded();
        }
    }

    @Unique
    private boolean solace$hasLandingSurfaceBelow(Entity entity) {
        BlockPos base = BlockPos.containing(entity.getX(), entity.getBoundingBox().minY - 0.12D, entity.getZ());

        for (int dy = 0; dy >= -12; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = entity.level().getBlockState(pos);
                    if (!state.isAir() && state.getCollisionShape(entity.level(), pos).max(Direction.Axis.Y) > 0.0D) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Inject(method = {"hurt", "m_6469_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$preventIntroDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity.getEntityData().get(SOLACE$INTRO_BALLOON) && !entity.getEntityData().get(SOLACE$LANDED)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$preventIntroDestroy(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.getEntityData().get(SOLACE$INTRO_BALLOON) && !entity.getEntityData().get(SOLACE$LANDED)) {
            ci.cancel();
        }
    }

    @Inject(method = "getTargetDirection", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$getIntroTargetDirection(NewWindSavedData windData, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;

        if (!entity.getEntityData().get(SOLACE$INTRO_BALLOON)) {
            return;
        }

        if (entity.getEntityData().get(SOLACE$LANDED)) {
            entity.setDeltaMovement(Vec3.ZERO);
            cir.setReturnValue(Vec3.ZERO);
            return;
        }

        double targetX = entity.getEntityData().get(SOLACE$TARGET_X);
        double targetY = entity.getEntityData().get(SOLACE$TARGET_Y);
        double targetZ = entity.getEntityData().get(SOLACE$TARGET_Z);

        Vec3 delta = new Vec3(targetX - entity.getX(), targetY - entity.getY(), targetZ - entity.getZ());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        Vec3 approach = new Vec3(entity.getEntityData().get(SOLACE$APPROACH_X), 0.0D, entity.getEntityData().get(SOLACE$APPROACH_Z));
        Vec3 horizontal = approach.lengthSqr() > 0.000001D
                ? approach.normalize()
                : horizontalDistance > 0.001D ? new Vec3(delta.x, 0.0D, delta.z).normalize() : Vec3.ZERO;
        double speed = horizontalDistance < 180.0D ? 0.18D : 0.26D;
        double descent = entity.getY() > targetY + 28.0D ? -0.045D : -0.012D;

        cir.setReturnValue(new Vec3(horizontal.x * speed, descent, horizontal.z * speed));
    }
}
