package com.solace.mixin;

import com.Polarice3.Goety.utils.MobUtil;
import com.nyfaria.nyfsspiders.common.entity.mob.IClimberEntity;
import com.nyfaria.nyfsspiders.common.entity.mob.Orientation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.Polarice3.Goety.common.entities.hostile.WebSpider$WebShootGoal", remap = false)
public abstract class WebSpiderShootGoalMixin {

    @Redirect(
        method = "m_8037_",
        at = @At(
            value = "INVOKE",
            target = "Lcom/Polarice3/Goety/utils/MobUtil;instaLook(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/Entity;)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void solace$useClimberAwareLook(Mob mob, Entity target) {
        if (mob instanceof IClimberEntity climber) {
            Vec3 look = new Vec3(
                target.getX() - mob.getX(),
                target.getY(0.5D) - mob.getEyeY(),
                target.getZ() - mob.getZ()
            );

            Orientation orientation = climber.getOrientation();
            if (orientation != null && look.lengthSqr() > 1.0E-6D) {
                Pair<Float, Float> localRotation = orientation.getLocalRotation(look);
                float yaw = localRotation.getLeft();
                float pitch = localRotation.getRight();

                mob.setYRot(yaw);
                mob.setXRot(pitch);
                mob.yBodyRot = yaw;
                mob.yBodyRotO = yaw;
                mob.yHeadRot = yaw;
                mob.yHeadRotO = yaw;
            }
            return;
        }

        MobUtil.instaLook(mob, target);
    }
}
