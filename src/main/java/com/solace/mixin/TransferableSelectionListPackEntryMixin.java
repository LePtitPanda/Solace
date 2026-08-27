package com.solace.mixin;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class TransferableSelectionListPackEntryMixin {
    @Shadow
    @Final
    private TransferableSelectionList parent;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    @Final
    private PackSelectionModel.Entry pack;

    @Inject(method = "render", at = @At("HEAD"))
    private void solace$renderPackCard(GuiGraphics graphics, int index, int top, int left,
                                       int width, int height, int mouseX, int mouseY,
                                       boolean hovered, float partialTick, CallbackInfo ci) {
        WorldCreationThemeRenderer.renderListRowBackdrop(graphics, left, top, width, height, hovered);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void solace$renderPackControls(GuiGraphics graphics, int index, int top, int left,
                                           int width, int height, int mouseX, int mouseY,
                                           boolean hovered, float partialTick, CallbackInfo ci) {
        boolean showControls = !this.pack.isFixedPosition() || !this.pack.isRequired();
        showControls &= this.minecraft.options.touchscreen().get() || hovered
                || this.parent.getSelected() == (Object) this && this.parent.isFocused();
        if (showControls) {
            WorldCreationThemeRenderer.renderResourcePackControls(graphics, left, top, mouseX, mouseY,
                    this.pack.canSelect(), this.pack.canUnselect(),
                    this.pack.canMoveUp(), this.pack.canMoveDown());
        }
    }
}
