package com.solace.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Shared layout for Solace's setup screens. The themed backdrop, gold header and
 * footer come from {@link WorldCreationThemeRenderer} via the screen mixins; this
 * class lays out a subtitle, a row of {@link SolaceChoiceCard}s and a footnote.
 */
public abstract class SolaceSetupScreen extends Screen implements SolaceThemedScreen {
    protected static final int BUTTON_HEIGHT = 20;
    private static final int CARD_GAP = 12;
    private static final int CARD_MAX_WIDTH = 170;
    private static final int CARD_MAX_HEIGHT = 140;

    protected final List<SolaceChoiceCard> cards = new ArrayList<>();
    private final Component subtitle;
    private final Component footnote;

    protected SolaceSetupScreen(Component title, Component subtitle, Component footnote) {
        super(title);
        this.subtitle = subtitle;
        this.footnote = footnote;
    }

    protected int buttonRowY() {
        return this.height - 30;
    }

    /** Lays out {@code count} cards in one centered row between the subtitle and the footer. */
    protected void layoutCards() {
        int count = cards.size();
        if (count == 0) {
            return;
        }
        int available = Math.min(this.width - 40, count * CARD_MAX_WIDTH + (count - 1) * CARD_GAP);
        int cardWidth = Math.max(60, (available - (count - 1) * CARD_GAP) / count);
        int rowWidth = cardWidth * count + CARD_GAP * (count - 1);
        int left = (this.width - rowWidth) / 2;

        int top = WorldCreationThemeRenderer.MENU_CONTENT_TOP + 8;
        int footerY = buttonRowY() - 18;
        int bottomLimit = footerY - (footnote == null ? 10 : 22);
        int cardHeight = Mth.clamp(bottomLimit - top, 56, CARD_MAX_HEIGHT);
        int freeSpace = bottomLimit - top - cardHeight;
        top += Math.max(0, freeSpace / 3);

        for (int i = 0; i < count; i++) {
            SolaceChoiceCard card = cards.get(i);
            card.setBounds(left + i * (cardWidth + CARD_GAP), top, cardWidth, cardHeight);
            addRenderableWidget(card);
        }
    }

    protected void select(SolaceChoiceCard selected) {
        for (SolaceChoiceCard card : cards) {
            card.setSelected(card == selected);
        }
    }

    /**
     * Washes the backdrop toward the selected card's colors, the way the difficulty
     * screen shifts its background per difficulty.
     */
    protected void renderTint(GuiGraphics graphics) {
        for (SolaceChoiceCard card : cards) {
            if (card.isSelected()) {
                int background = card.selectedBackground() & 0xFFFFFF;
                int accent = card.accent() & 0xFFFFFF;
                graphics.fill(0, 0, this.width, this.height, 0x70000000 | background);
                graphics.fillGradient(0, this.height / 2, this.width, this.height, 0x00000000, 0x1C000000 | accent);
                return;
            }
        }
    }

    protected int selectedAccent() {
        for (SolaceChoiceCard card : cards) {
            if (card.isSelected()) {
                return card.accent();
            }
        }
        return WorldCreationThemeRenderer.GOLD;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderTint(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        if (subtitle != null) {
            int y = WorldCreationThemeRenderer.MENU_HEADER_BOTTOM + 8;
            for (FormattedCharSequence line : this.font.split(subtitle, Math.min(360, this.width - 40))) {
                graphics.drawCenteredString(this.font, line, centerX, y, WorldCreationThemeRenderer.MUTED);
                y += this.font.lineHeight + 2;
            }
        }
        if (footnote != null) {
            int y = buttonRowY() - 18 - 6 - this.font.lineHeight;
            String text = this.font.plainSubstrByWidth(footnote.getString(), this.width - 20);
            graphics.drawCenteredString(this.font, text, centerX, y, 0xFF55557A);
        }
    }
}
