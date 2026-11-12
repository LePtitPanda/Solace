package com.solace.client.quests;

import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The mod's config screen: drag the sample tracker to where the real one should sit. */
public class QuestHudConfigScreen extends Screen {
    private static final int HEIGHT = 34;
    private final Screen parent;
    private final QuestClientConfig config = QuestClientConfig.get();
    private boolean dragging;
    private double grabX, grabY;

    public QuestHudConfigScreen(Screen parent) {
        super(Component.literal("Quest tracker position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal(config.hud ? "Tracker: shown" : "Tracker: hidden"), button -> {
            config.hud = !config.hud;
            button.setMessage(Component.literal(config.hud ? "Tracker: shown" : "Tracker: hidden"));
        }).bounds(this.width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            config.hudX = 1.0;
            config.hudY = 0.33;
        }).bounds(this.width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 + 54, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, WorldCreationThemeRenderer.GOLD);
        graphics.drawCenteredString(this.font, "Drag the box to move the quest tracker.", this.width / 2, 26,
                WorldCreationThemeRenderer.MUTED);
        int x = QuestHud.x(config, this.width), y = QuestHud.y(config, this.height);
        graphics.fill(x, y, x + QuestHud.WIDTH, y + HEIGHT, 0xC00C0C1C);
        graphics.fill(x, y, x + 2, y + HEIGHT, WorldCreationThemeRenderer.GOLD);
        graphics.drawString(this.font, "Quest title", x + 6, y + 3, WorldCreationThemeRenderer.GOLD, false);
        graphics.drawString(this.font, "First objective", x + 8, y + 14, WorldCreationThemeRenderer.TEXT, false);
        graphics.drawString(this.font, "Second objective", x + 8, y + 24, WorldCreationThemeRenderer.TEXT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = QuestHud.x(config, this.width), y = QuestHud.y(config, this.height);
        if (mouseX >= x && mouseX < x + QuestHud.WIDTH && mouseY >= y && mouseY < y + HEIGHT) {
            dragging = true;
            grabX = mouseX - x;
            grabY = mouseY - y;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            // Inverse of QuestHud.x / QuestHud.y.
            config.hudX = Mth.clamp((mouseX - grabX - 6) / Math.max(1, this.width - QuestHud.WIDTH - 12), 0.0, 1.0);
            config.hudY = Mth.clamp((mouseY - grabY - 6) / Math.max(1, this.height - 52), 0.0, 1.0);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        config.save();
        this.minecraft.setScreen(parent);
    }
}
