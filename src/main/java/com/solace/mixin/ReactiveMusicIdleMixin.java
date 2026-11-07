package com.solace.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the original decoder/volume path; only waits at its outer work loop. */
@Pseudo
@Mixin(targets = "circuitlord.reactivemusic.PlayerThread", remap = false)
public abstract class ReactiveMusicIdleMixin extends Thread {
    @Shadow private volatile boolean queued;
    @Shadow private volatile boolean kill;
    @Shadow public static volatile String currentSong;

    // The target starts its worker from its constructor: do not rely on a field initializer.
    @Unique private volatile Object solace$workSignal;
    @Unique private long solace$lastAttemptNanos;
    @Unique private static final long SOLACE_RETRY_NANOS = 50_000_000L;

    @Unique
    private Object solace$signal() {
        Object signal = solace$workSignal;
        if (signal == null) {
            synchronized (this) {
                signal = solace$workSignal;
                if (signal == null) solace$workSignal = signal = new Object();
            }
        }
        return signal;
    }

    @Redirect(method = "run", at = @At(value = "FIELD",
            target = "Lcircuitlord/reactivemusic/PlayerThread;kill:Z", opcode = Opcodes.GETFIELD),
            require = 1, allow = 1, remap = false)
    private boolean solace$awaitQueuedWork(@Coerce Object owner) {
        Object signal = solace$signal();
        synchronized (signal) {
            while (!kill) {
                long waitMillis = 100;
                if (queued && currentSong != null) {
                    long now = System.nanoTime();
                    long remaining = SOLACE_RETRY_NANOS - (now - solace$lastAttemptNanos);
                    if (solace$lastAttemptNanos == 0 || remaining <= 0) {
                        solace$lastAttemptNanos = now;
                        return false;
                    }
                    // A missing resource leaves queued=true; bound those retries as well.
                    waitMillis = Math.max(1, (remaining + 999_999L) / 1_000_000L);
                }
                try {
                    signal.wait(waitMillis);
                } catch (InterruptedException ignored) {
                    // forceKill interrupts before writing kill. Recheck its volatile flags.
                }
            }
        }
        return true;
    }

    @Inject(method = "play(Ljava/lang/String;)V", at = @At("RETURN"), remap = false)
    private void solace$wakeNewSong(String song, CallbackInfo ci) {
        Object signal = solace$signal();
        synchronized (signal) {
            solace$lastAttemptNanos = 0;
            signal.notifyAll();
        }
    }

    @Inject(method = {"resetPlayer", "forceKill"}, at = @At("RETURN"), remap = false)
    private void solace$wakeStateChange(CallbackInfo ci) {
        Object signal = solace$signal();
        synchronized (signal) {
            signal.notifyAll();
        }
    }
}
