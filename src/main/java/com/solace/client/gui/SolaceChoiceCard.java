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
 * A selectable card in the Difficulty Enhancement style: a framed panel
 * whose lines brighten when hovered or selected, with the card's accent in its
 * rivets and title. Each card can carry
 * its own accent and tinted background, like each difficulty does.
 */
public class SolaceChoiceCard extends AbstractButton {
    private static final int PADDING = 8;

    private final Component tag;
    private final Component description;
    private final Consumer<SolaceChoiceCard> onPress;
    private int accent = WorldCreationThemeRenderer.LIGHT;
    private int selectedBackground = 0xF01A1A1A;
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

        // A grey panel; the card's accent shows only in its rivets, title and tag.
        float emphasis = Math.max(hover * 0.55F, selection);
        SolaceArt.Tones tones = WorldCreationThemeRenderer.tones(true, emphasis).withRivet(accent);
        WorldCreationThemeRenderer.renderPanel(graphics, x, y, w, h,
                lerpArgb(selection, 0xE0050505, selectedBackground), tones,
                SolaceArt.PANEL_ORNATE);

        Font font = Minecraft.getInstance().font;
        int textX = x + PADDING + 3;
        int textWidth = Math.max(10, w - PADDING * 2 - 6);
        int textY = y + PADDING + 2;
        int bottom = y + h - PADDING - 2;

        int titleColor = lerpArgb(Math.max(selection, hover), WorldCreationThemeRenderer.TEXT, accent);
        String title = font.plainSubstrByWidth(getMessage().getString().toUpperCase(Locale.ROOT), textWidth);
        graphics.drawString(font, title, textX, textY, titleColor, false);
        textY += font.lineHeight + 2;

        if (tag != null && !tag.getString().isEmpty()) {
            String tagText = font.plainSubstrByWidth(tag.getString().toUpperCase(Locale.ROOT), textWidth);
            graphics.drawString(font, tagText, textX, textY, lerpArgb(0.3F, accent, 0xFF000000), false);
            textY += font.lineHeight + 1;
        }
        // Rivet and short rule under the title, like the rivet-capped rules on the panels.
        SolaceArt.rivet(graphics::fill, textX - 1, textY, tones);
        graphics.fill(textX + 3, textY + 1, textX + Math.min(textWidth, 44), textY + 2, SolaceArt.DIM);
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

    static int lerpArgb(float t, int from, int to) {
        float v = Mth.clamp(t, 0.0F, 1.0F);
        int a = Math.round(Mth.lerp(v, from >>> 24, to >>> 24));
        int r = Math.round(Mth.lerp(v, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(v, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(v, from & 0xFF, to & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
