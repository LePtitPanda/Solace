package com.solace.mixin;

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
                graphics.fill(this.x0, this.y0, this.x1, this.y1, 0xA60C0C1C);
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
        if (className.equals("net.minecraft.client.gui.screens.worldselection.SelectWorldScreen")
                || className.equals("net.minecraft.client.gui.screens.packs.PackSelectionScreen")) {
            graphics.fill(left - 2, top - 2, right, top + rowHeight + 2, 0xB3352A1A);
            graphics.fill(left - 2, top - 2, left + 1, top + rowHeight + 2,
                    WorldCreationThemeRenderer.GOLD);
            graphics.fill(left + 1, top + rowHeight, right, top + rowHeight + 2,
                    WorldCreationThemeRenderer.GOLD_DIM);
            ci.cancel();
            return;
        }
        graphics.fill(left, top - 2, right, top + rowHeight + 2, WorldCreationThemeRenderer.GOLD);
        graphics.fill(left + 1, top - 1, right - 1, top + rowHeight + 1,
                WorldCreationThemeRenderer.PANEL);
        ci.cancel();
    }
}
