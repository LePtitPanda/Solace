package com.solace.compat.betterinventory;

import dev.fallingcloud.betterinventory.client.ClientTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import party.lemons.sleeprework.SleepRework;
import party.lemons.sleeprework.config.SleepReworkConfig;

/**
 * Sleep Rework's tiredness in the Better Inventory style: moon, bar filling up to "can sleep" (100%), and the
 * percentage. The bar's colour follows Sleep Rework's own colours (sleepy at 100%, phantoms at its phantom level).
 */
final class TirednessStrip {
    private TirednessStrip() {
    }

    static Component render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        if (SleepRework.localSleepData == null || SleepRework.CONFIG == null) {
            return null;
        }
        SleepReworkConfig config = SleepRework.CONFIG;
        float tiredness = SleepRework.localSleepData.getTiredness();
        float sleep = config.playerConfig().minSleepLevel();
        float phantoms = config.phantomConfig().phantomSpawnTiredness();
        float max = config.playerConfig().maxTiredness();
        SleepReworkConfig.ClientConfig colors = config.clientConfig();
        int color = tiredness < sleep ? lerp(colors.color_1(), colors.color_2(), tiredness / sleep)
                : tiredness < phantoms ? lerp(colors.color_2(), colors.color_3(), (tiredness - sleep) / (phantoms - sleep))
                : lerp(colors.color_3(), colors.color_4(), (tiredness - phantoms) / Math.max(0.001F, max - phantoms));
        int percent = (int) (tiredness / sleep * 100);
        graphics.blit(BetterInventoryExtras.MOON_ICON, x, y - 2, 0, 0, 16, 16, 16, 16);
        BetterInventoryExtras.bar(graphics, x + 17, y + 3, 38, 5, tiredness / sleep, color);
        graphics.drawString(font, percent + "%", x + 58, y + 2, ClientTheme.panelTextDim(), false);
        return BetterInventoryExtras.over(x, y - 2, 78, 14, mouseX, mouseY)
                ? Component.translatable("sleeprework.display.tiredness", percent) : null;
    }

    private static int lerp(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
        int g = (int) ((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return r << 16 | g << 8 | b;
    }
}
