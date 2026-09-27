package com.solace.mixin;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfigBuilder;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import com.electronwill.nightconfig.core.io.WritingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.FileSystemException;

/**
 * Prevents KnightLib's generated configs from rewriting themselves hundreds of
 * times during mod construction. On Windows, one of those autosaves can race a
 * short-lived memory-mapped reader and abort Forge before loading completes.
 */
@Pseudo
@Mixin(targets = "dev.xylonity.knightlib.config.impl.ConfigManager", remap = false)
public abstract class KnightLibConfigManagerMixin {
    private static final Logger SOLACE$LOGGER = LogManager.getLogger("Solace/KnightLibConfig");
    private static final int SOLACE$MAX_SAVE_ATTEMPTS = 8;

    @Redirect(
            method = "loadOrCreate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/electronwill/nightconfig/core/file/CommentedFileConfigBuilder;autosave()Lcom/electronwill/nightconfig/core/file/GenericBuilder;"
            ),
            require = 0,
            remap = false
    )
    private static GenericBuilder<?, ?> solace$saveGeneratedConfigOnce(CommentedFileConfigBuilder builder) {
        return builder;
    }

    @Redirect(
            method = "loadOrCreate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/electronwill/nightconfig/core/file/CommentedFileConfig;save()V"
            ),
            require = 0,
            remap = false
    )
    private static void solace$saveAndCloseGeneratedConfig(CommentedFileConfig config) {
        WritingException lastFailure = null;

        try {
            for (int attempt = 1; attempt <= SOLACE$MAX_SAVE_ATTEMPTS; attempt++) {
                try {
                    config.save();
                    if (attempt > 1) {
                        SOLACE$LOGGER.info("Saved {} after {} attempts", config.getNioPath(), attempt);
                    }
                    return;
                } catch (WritingException exception) {
                    lastFailure = exception;
                    if (!solace$isTransientWindowsMappingFailure(exception)
                            || attempt == SOLACE$MAX_SAVE_ATTEMPTS) {
                        throw exception;
                    }

                    SOLACE$LOGGER.warn("Config file {} is temporarily mapped; retrying save ({}/{})",
                            config.getNioPath(), attempt, SOLACE$MAX_SAVE_ATTEMPTS);
                    try {
                        Thread.sleep(25L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw exception;
                    }
                }
            }

            throw lastFailure;
        } finally {
            config.close();
        }
    }

    private static boolean solace$isTransientWindowsMappingFailure(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof FileSystemException fileSystemException) {
                String reason = fileSystemException.getReason();
                if (reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("user-mapped section")) {
                    return true;
                }
            }
        }
        return false;
    }
}
