package com.solace.mixin;

import com.solace.client.gui.SolaceArt;
import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
    @Shadow
    protected boolean renderBackground;

    @Shadow
    protected boolean renderTopAndBottom;

    @Shadow
    protected int x0;

    @Shadow
    protected int x1;

    @Shadow
    protected int y0;

    @Shadow
    protected int y1;

    @Shadow
    protected abstract int getScrollbarPosition();

    @Inject(method = "render", at = @At("HEAD"))
    private void solace$renderFrontEndBackdrop(GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (WorldCreationThemeRenderer.isFrontEndScreen(screen)) {
            if (this.y0 < WorldCreationThemeRenderer.MENU_CONTENT_TOP) {
                this.y0 = Math.min(WorldCreationThemeRenderer.MENU_CONTENT_TOP, this.y1 - 20);
            }
            int listBottomY = WorldCreationThemeRenderer.getListBottomY(screen);
            if (this.y1 > listBottomY) {
                this.y1 = Math.max(this.y0 + 20, listBottomY);
            }
            boolean spansScreen = this.x0 <= screen.width / 12 && this.x1 >= screen.width * 11 / 12;
            if (!spansScreen) {
                graphics.fill(this.x0, this.y0, this.x1, this.y1, 0xA60A0A0A);
            }
            this.renderBackground = false;
            this.renderTopAndBottom = false;
        }
    }

    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void solace$renderGoldSelection(GuiGraphics graphics, int top, int rowWidth, int rowHeight,
                                            int outerColor, int innerColor, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (!WorldCreationThemeRenderer.isFrontEndScreen(screen)) {
            return;
        }
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        int left = list.getRowLeft();
        int right = Math.min(left + rowWidth, this.getScrollbarPosition() - 3);
        right = Math.max(left + 4, right);
        String className = screen.getClass().getName();
        // Selected row: a lit frame around the row.
        int inset = className.equals("net.minecraft.client.gui.screens.worldselection.SelectWorldScreen")
                || className.equals("net.minecraft.client.gui.screens.packs.PackSelectionScreen") ? 2 : 0;
        int x0 = left - inset;
        int y0 = top - 2;
        int x1 = right;
        int y1 = top + rowHeight + 2;
        graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xB3242424);
        SolaceArt.frame(graphics::fill, x0, y0, x1 - x0, y1 - y0, SolaceArt.Tones.HOVER);
        ci.cancel();
    }
}
