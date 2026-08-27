package com.solace.mixin;

import de.keksuccino.fancymenu.util.properties.Property;
import de.keksuccino.fancymenu.util.resource.ResourceSupplier;
import de.keksuccino.fancymenu.util.resource.resources.video.IVideo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps FancyMenu's native video background transparent until WaterMedia has
 * uploaded a real frame. FancyMenu otherwise draws an opaque black rectangle
 * while its video texture is missing, hiding the still-image fallback below it.
 */
@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.customization.background.backgrounds.video.nativevideo.NativeVideoMenuBackground",
        priority = 2000, remap = false)
public abstract class FancyMenuNativeVideoBackgroundMixin {
    @Shadow(remap = false)
    public Property<ResourceSupplier<IVideo>> videoSupplier;

    @Inject(method = "m_88315_", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$showStillImageUntilVideoIsReady(GuiGraphics graphics, int mouseX, int mouseY,
                                                         float partialTick, CallbackInfo ci) {
        ResourceSupplier<IVideo> supplier = this.videoSupplier == null ? null : this.videoSupplier.get();
        IVideo video = supplier == null ? null : supplier.get();
        ResourceLocation texture = video == null ? null : video.getResourceLocation();

        if (texture == null || IVideo.MISSING_TEXTURE_LOCATION.equals(texture)) {
            ci.cancel();
        }
    }
}
