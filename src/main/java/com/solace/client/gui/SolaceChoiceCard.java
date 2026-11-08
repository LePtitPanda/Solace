package com.solace.client.gui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * A selectable card in the Difficulty Enhancement style: dark panel, accent rail,
 * accent border on hover and corner brackets when selected. Each card can carry
 * its own accent and tinted background, like each difficulty does.
 */
public class SolaceChoiceCard extends AbstractButton {
    private static final int PADDING = 8;

    private final Component tag;
    private final Component description;
    private final Consumer<SolaceChoiceCard> onPress;
    private int accent = WorldCreationThemeRenderer.GOLD;
    private int selectedBackground = 0xF01A1508;
    private boolean selected;
    private float hover;
    private float selection;
    private long lastFrame;

    public SolaceChoiceCard(int x, int y, int width, int height, Component title, Component tag,
                            Component description, Consumer<SolaceChoiceCard> onPress) {
        super(x, y, width, height, title);
        this.tag = tag;
        this.description = description;
        this.onPress = onPress;
    }

    /** Sets the accent (RGB) and the dark background (RGB) shown while selected. */
    public SolaceChoiceCard withColors(int accent, int background) {
        this.accent = 0xFF000000 | accent;
        this.selectedBackground = 0xF0000000 | (background & 0xFFFFFF);
        return this;
    }

    public int accent() {
        return accent;
    }

    public int selectedBackground() {
        return selectedBackground;
    }

    public void setBounds(int x, int y, int width, int height) {
        setX(x);
        setY(y);
        this.width = width;
        this.height = height;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float delta = lastFrame == 0L ? 1.0F : Mth.clamp((now - lastFrame) / 1000.0F, 0.0F, 0.1F);
        lastFrame = now;
        hover = approach(hover, isHoveredOrFocused() ? 1.0F : 0.0F, delta * 8.0F);
        selection = approach(selection, selected ? 1.0F : 0.0F, delta * 8.0F);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        graphics.fill(x, y, x + w, y + h, lerpArgb(selection, 0xD90C0C1C, selectedBackground));
        // Soft accent wash from the rail, like the difficulty rows.
        int washAlpha = Math.round(34.0F * Math.max(hover * 0.6F, selection));
        if (washAlpha > 0) {
            graphics.fillGradient(x, y, x + w, y + h, washAlpha << 24 | (accent & 0xFFFFFF), 0x00000000);
        }
        float emphasis = Math.max(hover * 0.55F, selection);
        if (emphasis > 0.02F) {
            int alpha = Math.round(200.0F * emphasis);
            border(graphics, x, y, w, h, alpha << 24 | (accent & 0xFFFFFF));
        }
        int rail = 3 + Math.round(3.0F * hover + 4.0F * selection);
        graphics.fill(x, y, x + rail, y + h, accent);
        if (selection > 0.02F) {
            int alpha = Math.round(255.0F * selection);
            WorldCreationThemeRenderer.renderBracketPanel(graphics, x - 4, y - 4, w + 8, h + 8,
                    alpha << 24 | (accent & 0xFFFFFF));
        }

        Font font = Minecraft.getInstance().font;
        int textX = x + rail + PADDING;
        int textWidth = Math.max(10, w - rail - PADDING * 2);
        int textY = y + PADDING;
        int bottom = y + h - PADDING;

        int titleColor = lerpArgb(Math.max(selection, hover), WorldCreationThemeRenderer.TEXT, accent);
        String title = font.plainSubstrByWidth(getMessage().getString().toUpperCase(Locale.ROOT), textWidth);
        graphics.drawString(font, title, textX, textY, titleColor, false);
        textY += font.lineHeight + 2;

        if (tag != null && !tag.getString().isEmpty()) {
            String tagText = font.plainSubstrByWidth(tag.getString().toUpperCase(Locale.ROOT), textWidth);
            graphics.drawString(font, tagText, textX, textY, lerpArgb(0.3F, accent, 0xFF000000), false);
            textY += font.lineHeight + 1;
        }
        graphics.fill(textX, textY + 2, textX + Math.min(textWidth, 40), textY + 3,
                lerpArgb(0.55F, accent, 0xFF000000));
        textY += 8;

        int bodyColor = lerpArgb(Math.max(hover, selection), WorldCreationThemeRenderer.MUTED,
                WorldCreationThemeRenderer.TEXT);
        List<FormattedCharSequence> lines = font.split(description, textWidth);
        for (FormattedCharSequence line : lines) {
            if (textY + font.lineHeight > bottom) {
                break;
            }
            graphics.drawString(font, line, textX, textY, bodyColor, false);
            textY += font.lineHeight + 1;
        }
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.empty().append(getMessage()).append(". ").append(description);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, color);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        graphics.fill(x - 1, y, x, y + h, color);
        graphics.fill(x + w, y, x + w + 1, y + h, color);
    }

    static int lerpArgb(float t, int from, int to) {
        float v = Mth.clamp(t, 0.0F, 1.0F);
        int a = Math.round(Mth.lerp(v, from >>> 24, to >>> 24));
        int r = Math.round(Mth.lerp(v, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(v, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(v, from & 0xFF, to & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
