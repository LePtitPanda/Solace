package com.solace.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
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
 * A confirm button that must be held, like Difficulty Enhancement's confirm. It is a primary
 * button whose flourish center takes the current accent color; a soft fill sweeps across
 * while the mouse button, Enter or Space is held.
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

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // A primary button. Progress is monochrome; the accent only tints the flourish's
        // center, so the button still hints at the current selection.
        float glow = Math.max(hover, progress);
        SolaceArt.Tones tones = WorldCreationThemeRenderer.tones(active, glow)
                .withRivet(active ? 0xFF000000 | accent.getAsInt() : SolaceArt.LINE);
        SolaceArt.Sink sink = graphics::fill;
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, active ? 0xE60A0A0A : 0xC0050505);
        SolaceArt.progress(sink, x, y, w, h, progress);
        SolaceArt.primaryFrame(sink, x, y, w, h, tones);

        Font font = Minecraft.getInstance().font;
        int textColor = active ? SolaceChoiceCard.lerpArgb(glow, 0xFFC8C8C8, WorldCreationThemeRenderer.TITLE)
                : 0xFF4A4A4A;
        String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, w - 30));
        graphics.drawCenteredString(font, text, x + w / 2, y + (h - font.lineHeight) / 2 + 1, textColor);
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
