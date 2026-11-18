package com.solace.client.quests;

import com.solace.client.gui.SolaceArt;
import com.solace.client.gui.WorldCreationThemeRenderer;
import com.solace.quests.network.QuestNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/** Quest started / updated / completed / failed and reputation rank changes, in Solace colours. */
public class QuestToast implements Toast {
    private final QuestNetwork.Toast toast;

    public QuestToast(QuestNetwork.Toast toast) {
        this.toast = toast;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceLastVisible) {
        Font font = component.getMinecraft().font;
        int accent = toast.kind().equals("failed") ? 0xFFAA4444 : WorldCreationThemeRenderer.TITLE;
        SolaceArt.Tones tones = SolaceArt.Tones.NORMAL.withRivet(accent);
        SolaceArt.panel(graphics::fill, 0, 0, width(), height(), 0xF0050505, tones, SolaceArt.PANEL_PLAIN);
        SolaceArt.rivet(graphics::fill, width() - 7, 4, tones);
        int textX = ClientQuests.icon(graphics, toast.icon(), 8, 8) ? 30 : 10;
        graphics.drawString(font, toast.title(), textX, 7, accent, false);
        graphics.drawString(font, font.plainSubstrByWidth(toast.subtitle().getString(), width() - textX - 6), textX, 18,
                WorldCreationThemeRenderer.TEXT, false);
        return timeSinceLastVisible >= 4000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
