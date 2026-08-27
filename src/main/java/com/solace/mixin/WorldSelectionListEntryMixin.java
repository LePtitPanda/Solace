package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldSelectionListEntryMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelSummary summary;

    @Inject(method = "render", at = @At("HEAD"))
    private void solace$renderWorldCard(GuiGraphics graphics, int index, int top, int left,
                                        int width, int height, int mouseX, int mouseY,
                                        boolean hovered, float partialTick, CallbackInfo ci) {
        WorldCreationThemeRenderer.renderListRowBackdrop(graphics, left, top, width, height, hovered);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void solace$renderWorldJoinControl(GuiGraphics graphics, int index, int top, int left,
                                               int width, int height, int mouseX, int mouseY,
                                               boolean hovered, float partialTick, CallbackInfo ci) {
        boolean normalWorld = !(this.summary instanceof LevelSummary.SymlinkLevelSummary)
                && !this.summary.isLocked()
                && !this.summary.requiresManualConversion()
                && !this.summary.markVersionInList();
        if (normalWorld && (this.minecraft.options.touchscreen().get() || hovered)) {
            boolean iconHovered = mouseX >= left && mouseX < left + 32
                    && mouseY >= top && mouseY < top + 32;
            WorldCreationThemeRenderer.renderWorldJoinControl(graphics, left, top, iconHovered);
        }
    }
}
