package com.solace.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TFMG 1.0.2f replaces Create's goggle overlay with a HEAD inject that casts {@code mc.hitResult} to
 * BlockHitResult unconditionally. Looking at an entity while wearing goggles then throws a
 * ClassCastException on every HUD frame (stack trace + log write each time). Create's own renderer
 * returns immediately for non-block hits, so returning first, ahead of TFMG's callback, keeps the
 * intended behaviour and removes the exception.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer", priority = 500)
public abstract class TfmgGoggleOverlayGuardMixin {
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, remap = false)
    private static void solace$skipNonBlockHits(CallbackInfo ci) {
        if (!(Minecraft.getInstance().hitResult instanceof BlockHitResult)) {
            ci.cancel();
        }
    }
}
