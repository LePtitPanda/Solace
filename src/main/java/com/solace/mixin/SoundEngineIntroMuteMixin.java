package com.solace.mixin;

import com.solace.client.IntroHoldClient;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the first-join loading screen is up, every sound category except music is at volume zero. This also
 * silences mods that play sounds without the Forge sound event (AmbientSounds, Sounds).
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineIntroMuteMixin {
    @Inject(method = "getVolume", at = @At("HEAD"), cancellable = true)
    private void solace$muteDuringIntro(SoundSource source, CallbackInfoReturnable<Float> cir) {
        if (IntroHoldClient.muting() && source != null && !IntroHoldClient.keeps(source)) {
            cir.setReturnValue(0.0F);
        }
    }
}
