package com.solace.mixin;

import de.keksuccino.fancymenu.util.properties.Property;
import de.keksuccino.fancymenu.util.resource.ResourceSupplier;
import de.keksuccino.fancymenu.util.resource.resources.video.IVideo;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps FancyMenu's still-image fallback visible behind the native video.
 * FancyMenu otherwise draws an opaque black rectangle before asking WaterMedia
 * for the video resource, hiding the fallback while the first frame is decoded.
 */
@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.customization.background.backgrounds.video.nativevideo.NativeVideoMenuBackground",
        priority = 2000, remap = false)
public abstract class FancyMenuNativeVideoBackgroundMixin {
    @Shadow(remap = false)
    public Property<ResourceSupplier<IVideo>> videoSupplier;

    @Redirect(
            method = "m_88315_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;m_280509_(IIIII)V",
                    ordinal = 0
            ),
            remap = false
    )
    private void solace$leaveStillImageVisibleBehindVideo(GuiGraphics graphics, int left, int top,
                                                            int right, int bottom, int color) {
        if (!solace$isAstralisVideo()) {
            graphics.fill(left, top, right, bottom, color);
        }
    }

    /**
     * FancyMenu's watchdog stops videos that have not rendered for eleven
     * seconds. WaterMedia 3.0.0.21 can leave that stopped player unusable when
     * the title screen is opened again, so retain this one preloaded video and
     * let FancyMenu's normal close/open hooks pause and resume it instead.
     */
    @Inject(method = "shouldSkipWatchdogAutoClear", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$keepAstralisVideoAliveAcrossMenus(CallbackInfoReturnable<Boolean> cir) {
        if (solace$isAstralisVideo()) {
            cir.setReturnValue(true);
        }
    }

    private boolean solace$isAstralisVideo() {
        ResourceSupplier<IVideo> supplier = this.videoSupplier == null ? null : this.videoSupplier.get();
        String source = supplier == null ? "" : supplier.getSourceWithPrefix().replace('\\', '/');
        return source.endsWith("config/fancymenu/assets/astralismenu.mp4");
    }
}
