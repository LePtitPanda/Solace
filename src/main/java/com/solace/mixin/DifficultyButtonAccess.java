package com.solace.mixin;

import com.misanthropy.linggango.difficulty_enhancement.LinggangoEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.misanthropy.linggango.difficulty_enhancement.client.DifficultySelectionScreen$DifficultyButton", remap = false)
public interface DifficultyButtonAccess {
    @Accessor("difficulty")
    LinggangoEvents.DifficultyDef solace$getDifficulty();
}
