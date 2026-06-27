package com.solace.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.Polarice3.Goety.common.entities.hostile.WebSpider", remap = false)
public abstract class WebSpiderClientRotationMixin {

    public float getTargetYaw(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean teleport) {
        return yaw;
    }

    public float getTargetPitch(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean teleport) {
        return pitch;
    }

    public float getTargetHeadYaw(float yaw, int interpolationSteps) {
        return yaw;
    }
}
