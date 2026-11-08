package com.solace.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Drippy loading screen plays the menu video before Minecraft's sound engine
 * exists. FancyMenu always asks WaterMedia for an audio engine, and WaterMedia's
 * first alGenBuffers call before OpenAL is set up permanently breaks LWJGL's OpenAL
 * binding for the session: the video fails and Minecraft then loads with no sound.
 * Until the sound engine is loaded, create video players without audio. The
 * Astralis menu video is muted, so nothing audible is lost.
 */
@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.util.resource.resources.video.Mp4Video", remap = false)
public abstract class FancyMenuEarlyVideoAudioMixin {
    @ModifyArg(method = "createPlayerIfPossible", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lde/keksuccino/fancymenu/util/watermedia/WatermediaReflectionBridge;createPlayer(Ljava/lang/Object;Ljava/lang/Thread;Ljava/util/concurrent/Executor;ZZ)Ljava/lang/Object;",
                    remap = false),
            index = 4)
    private boolean solace$noAudioBeforeSoundEngine(boolean audio) {
        return audio && solace$isSoundEngineLoaded();
    }

    private static boolean solace$isSoundEngineLoaded() {
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager manager = minecraft == null ? null : minecraft.getSoundManager();
        if (manager == null) {
            return false;
        }
        return ((SoundEngineAccessor) ((SoundManagerAccessor) manager).solace$getSoundEngine()).solace$isLoaded();
    }
}
