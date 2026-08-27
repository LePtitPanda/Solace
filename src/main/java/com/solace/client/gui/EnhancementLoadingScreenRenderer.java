package com.solace.client.gui;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class EnhancementLoadingScreenRenderer {
    private static final Map<Screen, Long> START_TIMES = new WeakHashMap<>();

    private EnhancementLoadingScreenRenderer() {
    }

    public static void render(Screen screen, GuiGraphics graphics, Component status) {
        WorldCreationThemeRenderer.renderBackdrop(screen, graphics);

        long now = Util.getMillis();
        long startedAt = START_TIMES.computeIfAbsent(screen, ignored -> now);
        long elapsed = now - startedAt;
        Font font = Minecraft.getInstance().font;
        int centerX = screen.width / 2;
        int centerY = screen.height / 2;

        String base = status.getString().replaceAll("\\.+$", "").trim().toUpperCase(Locale.ROOT);
        int dots = (int) (elapsed / 400L % 4L);
        String text = base + ".".repeat(dots);
        graphics.drawCenteredString(font, Component.literal(text), centerX, centerY + 8,
                0xB8DDDDEE);

        int barWidth = 100;
        int barX = centerX - barWidth / 2;
        int barY = centerY + 25;
        float progress = (elapsed % 2200L) / 2200.0F;
        progress = 1.0F - (1.0F - progress) * (1.0F - progress);
        graphics.fill(barX, barY, barX + barWidth, barY + 3, 0x596A4E18);
        graphics.fill(barX, barY, barX + Math.max(1, Math.round(barWidth * Mth.clamp(progress, 0.0F, 1.0F))),
                barY + 3, 0xB3D4A843);
    }
}
