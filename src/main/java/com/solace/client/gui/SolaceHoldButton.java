package com.solace.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.IntSupplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * A confirm button that must be held, like Difficulty Enhancement's confirm. It is
 * drawn entirely in the current accent color, and a fill sweeps across while the
 * mouse button, Enter or Space is held.
 */
public class SolaceHoldButton extends AbstractButton {
    /** Difficulty Enhancement's normalConfirmTime is 35 ticks. */
    private static final long HOLD_MILLIS = 1750L;

    private final Runnable onConfirm;
    private final IntSupplier accent;
    private long holdStart = -1L;
    private boolean heldByKey;
    private int heldKey;
    private float progress;
    private float hover;
    private long lastFrame;

    public SolaceHoldButton(int x, int y, int width, int height, Component message, IntSupplier accent,
                            Runnable onConfirm) {
        super(x, y, width, height, message);
        this.accent = accent;
        this.onConfirm = onConfirm;
    }

    @Override
    public void onPress() {
        // Starts the hold; confirmation happens in render once the hold completes.
        if (holdStart < 0L) {
            holdStart = Util.getMillis();
        }
    }

    /** Drops any hold in progress, for example when the selection changes. */
    public void cancelHold() {
        holdStart = -1L;
        heldByKey = false;
        progress = 0.0F;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (active && visible && isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            heldByKey = true;
            heldKey = keyCode;
            onPress();
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateHold(mouseX, mouseY);

        long now = Util.getMillis();
        float delta = lastFrame == 0L ? 1.0F : Mth.clamp((now - lastFrame) / 1000.0F, 0.0F, 0.1F);
        lastFrame = now;
        // Mouse hover (or an active key hold) only: a leftover click focus must not
        // keep the button looking hovered after the player moves on.
        boolean lit = isHovered() || holdStart >= 0L;
        hover = lit ? Math.min(1.0F, hover + delta * 8.0F) : Math.max(0.0F, hover - delta * 8.0F);

        int color = 0xFF000000 | (accent.getAsInt() & 0xFFFFFF);
        int rgb = color & 0xFFFFFF;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        PoseStack pose = graphics.pose();
        float scale = 1.0F + hover * 0.018F;
        float cx = x + w / 2.0F;
        float cy = y + h / 2.0F;
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-cx, -cy, 0.0F);

        graphics.fill(x, y, x + w, y + h, active ? 0xE60C0C1C : 0xB607070F);
        if (progress > 0.0F) {
            int fill = Math.round(w * progress);
            graphics.fill(x, y, x + fill, y + h, 0x48000000 | rgb);
            graphics.fill(x, y + h - 2, x + fill, y + h, color);
        }
        if (hover > 0.02F) {
            int alpha = Math.round(150.0F * hover);
            int border = alpha << 24 | rgb;
            graphics.fill(x - 1, y - 1, x + w + 1, y, border);
            graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, border);
            graphics.fill(x - 1, y, x, y + h, border);
            graphics.fill(x + w, y, x + w + 1, y + h, border);
        }
        int rail = 3 + Math.round(5.0F * hover);
        graphics.fill(x, y, x + rail, y + h, active ? color : 0xFF3A321F);

        Font font = Minecraft.getInstance().font;
        int textColor = active
                ? SolaceChoiceCard.lerpArgb(Math.max(hover, progress), WorldCreationThemeRenderer.MUTED,
                        WorldCreationThemeRenderer.TEXT)
                : 0xFF444455;
        String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, w - rail - 12));
        graphics.drawCenteredString(font, text, x + w / 2 + rail / 2, y + (h - font.lineHeight) / 2 + 1, textColor);
        pose.popPose();
    }

    private void updateHold(int mouseX, int mouseY) {
        if (holdStart < 0L) {
            return;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean stillHeld = heldByKey
                ? InputConstants.isKeyDown(window, heldKey)
                : GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                        && isMouseOver(mouseX, mouseY);
        if (!stillHeld || !active) {
            cancelHold();
            return;
        }
        progress = Math.min(1.0F, (Util.getMillis() - holdStart) / (float) HOLD_MILLIS);
        if (progress >= 1.0F) {
            cancelHold();
            onConfirm.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
