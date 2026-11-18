package com.solace.mixin;

import com.solace.client.quests.ClientPonderUnlocks;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A ponder the player has no knowledge of does not exist: no "hold to ponder" on the tooltip, greyed out and
 * not clickable in the ponder index, tag pages and /ponder. Every way into a scene asks this first.
 */
@Pseudo
@Mixin(targets = "net.createmod.ponder.foundation.registration.PonderSceneRegistry", remap = false)
public abstract class PonderSceneLockMixin {
    @Inject(method = "doScenesExistForId", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$knowledge(ResourceLocation id, CallbackInfoReturnable<Boolean> cir) {
        if (ClientPonderUnlocks.locked(id)) {
            cir.setReturnValue(false);
        }
    }
}
