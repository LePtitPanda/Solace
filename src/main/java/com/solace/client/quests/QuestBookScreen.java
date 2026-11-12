package com.solace.client.quests;

import com.solace.client.gui.SolaceHoldButton;
import com.solace.client.gui.WorldCreationThemeRenderer;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.network.Views.ChapterView;
import com.solace.quests.network.Views.FactionView;
import com.solace.quests.network.Views.ObjectiveView;
import com.solace.quests.network.Views.QuestView;
import com.solace.quests.network.Views.RewardView;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static com.solace.client.gui.WorldCreationThemeRenderer.GOLD;
import static com.solace.client.gui.WorldCreationThemeRenderer.GOLD_DIM;
import static com.solace.client.gui.WorldCreationThemeRenderer.MUTED;
import static com.solace.client.gui.WorldCreationThemeRenderer.PANEL;
import static com.solace.client.gui.WorldCreationThemeRenderer.TEXT;

/**
 * The quest book: chapters on the left, a pannable and zoomable quest graph in the middle, the
 * selected quest's details on the right; plus Journal and Factions tabs. Purely a view of
 * {@link ClientQuests}; every button sends an intent the server re-checks.
 */
public class QuestBookScreen extends Screen {
    private enum Tab { QUESTS, JOURNAL, FACTIONS }

    private static final int LEFT = 150;
    private static final int RIGHT = 210;
    private static final int TOP = 30;
    private static final int GRID = 46;
    private static final int NODE = 26;
    private static final int[] STATUS_COLOR = {0xFF3A3A55, TEXT, GOLD, 0xFFFFE9A8, GOLD_DIM, 0xFFAA4444};
    private static final String[] STATUS_NAME = {"Locked", "Available", "In progress", "Rewards ready", "Completed",
            "Failed"};

    // Remembered while the game runs, so the book reopens where it was left.
    private static Tab tab = Tab.QUESTS;
    private static ResourceLocation chapter;
    private static ResourceLocation selected;
    private static double panX, panY, zoom = 1.0;

    private double scroll;
    private double detailScroll;
    private boolean dragging;
    private String builtFor = "";
    /** Screen rows of clickable things drawn this frame: x0, y0, x1, y1 and what a click does. */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
    }

    private record Line(FormattedCharSequence text, int color, int indent, Runnable click) {
    }

    public QuestBookScreen() {
        super(Component.literal("Quests"));
    }

    private static final class BookButton extends AbstractButton {
        private final Runnable action;
        private final boolean selected;

        BookButton(int x, int y, int width, int height, Component message, boolean selected, Runnable action) {
            super(x, y, width, height, message);
            this.action = action;
            this.selected = selected;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            WorldCreationThemeRenderer.renderButton(this, graphics, selected, isHoveredOrFocused());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private QuestView quest() {
        return selected == null ? null : ClientQuests.QUESTS.get(selected);
    }

    private String signature() {
        QuestView quest = quest();
        return tab + "|" + (quest == null ? "" : quest.id() + "|" + quest.status() + "|" + quest.tracked());
    }

    @Override
    protected void init() {
        builtFor = signature();
        int x = 8;
        for (Tab value : Tab.values()) {
            String name = value == Tab.QUESTS ? "Quests" : value == Tab.JOURNAL ? "Journal" : "Factions";
            addRenderableWidget(new BookButton(x, 6, 44, 18, Component.literal(name), tab == value, () -> {
                tab = value;
                scroll = 0;
                rebuildWidgets();
            }));
            x += 46;
        }
        QuestView quest = quest();
        if (tab != Tab.QUESTS || quest == null) {
            return;
        }
        int buttonX = this.width - RIGHT + 10;
        int buttonY = this.height - 30;
        int buttonWidth = (RIGHT - 26) / 2;
        if (quest.status() == QuestView.CLAIMABLE) {
            addRenderableWidget(new BookButton(buttonX, buttonY, RIGHT - 20, 20, Component.literal("Claim rewards"),
                    true, () -> QuestNetwork.intent(QuestNetwork.CLAIM, quest.id().toString(), "", 0, 0, 0)));
        } else if (quest.status() == QuestView.ACTIVE) {
            addRenderableWidget(new BookButton(buttonX, buttonY, buttonWidth, 20,
                    Component.literal(quest.tracked() ? "Untrack" : "Track"), quest.tracked(),
                    () -> QuestNetwork.intent(QuestNetwork.TRACK, quest.id().toString(), "", quest.tracked() ? 0 : 1,
                            0, 0)));
            if (quest.abandonable()) {
                addRenderableWidget(new SolaceHoldButton(buttonX + buttonWidth + 6, buttonY, buttonWidth, 20,
                        Component.literal("Abandon"), () -> 0xFFAA4444,
                        () -> QuestNetwork.intent(QuestNetwork.ABANDON, quest.id().toString(), "", 0, 0, 0)));
            }
        }
    }

    @Override
    public void tick() {
        // Views arrive while the book is open: rebuild buttons when the selected quest's state moved.
        if (!signature().equals(builtFor)) {
            rebuildWidgets();
        }
    }

    private ChapterView currentChapter() {
        for (ChapterView view : ClientQuests.chapters) {
            if (view.id().equals(chapter)) {
                return view;
            }
        }
        ChapterView first = ClientQuests.chapters.isEmpty() ? null : ClientQuests.chapters.get(0);
        chapter = first == null ? null : first.id();
        return first;
    }

    private boolean detailsOpen() {
        return tab == Tab.QUESTS && quest() != null;
    }

    private int canvasRight() {
        return this.width - (detailsOpen() ? RIGHT + 6 : 8);
    }

    private int nodeX(QuestView quest) {
        return (int) ((LEFT + canvasRight()) / 2.0 + panX + quest.x() * GRID * zoom);
    }

    private int nodeY(QuestView quest) {
        return (int) ((TOP + this.height - 10) / 2.0 + panY + quest.y() * GRID * zoom);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WorldCreationThemeRenderer.renderBackdrop(this, graphics);
        hits.clear();
        graphics.fill(0, TOP - 2, this.width, TOP - 1, GOLD_DIM);
        if (ClientQuests.chapters.isEmpty() && tab == Tab.QUESTS) {
            graphics.drawCenteredString(this.font, "No quests yet.", this.width / 2, this.height / 2, MUTED);
        } else if (tab == Tab.QUESTS) {
            renderChapters(graphics, mouseX, mouseY);
            renderCanvas(graphics, mouseX, mouseY);
            if (detailsOpen()) {
                renderDetails(graphics, quest());
            }
        } else if (tab == Tab.JOURNAL) {
            renderLines(graphics, journal(), 16, this.width - 16);
        } else {
            renderFactions(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderChapters(GuiGraphics graphics, int mouseX, int mouseY) {
        ChapterView current = currentChapter();
        int y = TOP + 6;
        for (ChapterView view : ClientQuests.chapters) {
            boolean active = current != null && view.id().equals(current.id());
            boolean hovered = mouseX >= 6 && mouseX < LEFT - 6 && mouseY >= y && mouseY < y + 22;
            graphics.fill(6, y, LEFT - 6, y + 22, active ? 0xF01A1508 : hovered ? 0xC0141428 : 0x900C0C1C);
            graphics.fill(6, y, 8, y + 22, active ? GOLD : GOLD_DIM);
            int textX = ClientQuests.icon(graphics, view.icon(), 12, y + 3) ? 32 : 14;
            int total = 0, done = 0;
            for (QuestView quest : ClientQuests.QUESTS.values()) {
                if (quest.chapter().equals(view.id()) && !quest.optional()) {
                    total++;
                    done += quest.status() == QuestView.COMPLETED || quest.status() == QuestView.CLAIMABLE ? 1 : 0;
                }
            }
            String count = done + "/" + total;
            String title = this.font.plainSubstrByWidth(view.title().getString(),
                    LEFT - 12 - textX - this.font.width(count) - 6);
            graphics.drawString(this.font, title, textX, y + 7, active ? GOLD : TEXT, false);
            graphics.drawString(this.font, count, LEFT - 10 - this.font.width(count), y + 7, MUTED, false);
            ResourceLocation id = view.id();
            hits.add(new Hit(6, y, LEFT - 6, y + 22, () -> {
                chapter = id;
                selected = null;
                panX = panY = 0;
                rebuildWidgets();
            }));
            y += 25;
        }
        if (current != null) {
            int textY = y + 8;
            for (FormattedCharSequence line : this.font.split(current.description(), LEFT - 20)) {
                graphics.drawString(this.font, line, 10, textY, MUTED, false);
                textY += 10;
            }
        }
    }

    private void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = LEFT, right = canvasRight(), top = TOP, bottom = this.height - 10;
        graphics.fill(left, top, right, bottom, 0x70070712);
        WorldCreationThemeRenderer.renderBracketPanel(graphics, left, top, right - left, bottom - top, GOLD_DIM);
        graphics.enableScissor(left + 2, top + 2, right - 2, bottom - 2);
        List<QuestView> quests = new ArrayList<>();
        for (QuestView quest : ClientQuests.QUESTS.values()) {
            if (quest.chapter().equals(chapter)) {
                quests.add(quest);
            }
        }
        // Dependency lines: an elbow from the required quest to the one that needs it.
        for (QuestView quest : quests) {
            for (ResourceLocation requiredId : quest.requires()) {
                QuestView required = ClientQuests.QUESTS.get(requiredId);
                if (required == null || !required.chapter().equals(chapter)) {
                    continue;
                }
                boolean done = required.status() == QuestView.COMPLETED || required.status() == QuestView.CLAIMABLE;
                int color = done ? GOLD_DIM : 0xFF2A2A44;
                int x0 = nodeX(required), y0 = nodeY(required), x1 = nodeX(quest), y1 = nodeY(quest);
                graphics.fill(Math.min(x0, x1), y0, Math.max(x0, x1) + 1, y0 + 1, color);
                graphics.fill(x1, Math.min(y0, y1), x1 + 1, Math.max(y0, y1) + 1, color);
            }
        }
        int half = (int) (NODE * zoom / 2);
        QuestView hovered = null;
        for (QuestView quest : quests) {
            int x = nodeX(quest), y = nodeY(quest);
            boolean over = mouseX >= x - half && mouseX < x + half && mouseY >= y - half && mouseY < y + half
                    && mouseX > left && mouseX < right && mouseY > top && mouseY < bottom;
            int color = STATUS_COLOR[Mth.clamp(quest.status(), 0, 5)];
            if (quest.status() == QuestView.CLAIMABLE && (Util.getMillis() / 400) % 2 == 0) {
                color = GOLD;
            }
            graphics.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1,
                    quest.id().equals(selected) || over ? 0xFFFFFFFF : color);
            graphics.fill(x - half, y - half, x + half, y + half, quest.status() == QuestView.LOCKED ? 0xFF090912 : PANEL);
            graphics.fill(x - half, y + half - 2, x + half, y + half, color);
            graphics.pose().pushPose();
            graphics.pose().translate(x - 8 * zoom, y - 8 * zoom, 0);
            graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
            if (!ClientQuests.icon(graphics, quest.icon(), 0, 0)) {
                graphics.drawString(this.font, "?", 5, 4, color, false);
            }
            graphics.pose().popPose();
            if (over) {
                hovered = quest;
            }
            ResourceLocation id = quest.id();
            if (x + half > left && x - half < right && y + half > top && y - half < bottom) {
                hits.add(new Hit(Math.max(left, x - half), Math.max(top, y - half), Math.min(right, x + half),
                        Math.min(bottom, y + half), () -> {
                    selected = id;
                    detailScroll = 0;
                    rebuildWidgets();
                }));
            }
        }
        graphics.disableScissor();
        if (hovered != null) {
            graphics.renderTooltip(this.font, List.of(hovered.title().getVisualOrderText(),
                    Component.literal(STATUS_NAME[Mth.clamp(hovered.status(), 0, 5)]).withStyle(ChatFormatting.GRAY)
                            .getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void renderDetails(GuiGraphics graphics, QuestView quest) {
        int left = this.width - RIGHT, right = this.width - 6, top = TOP, bottom = this.height - 36;
        graphics.fill(left, top, right, this.height - 6, 0xE60C0C1C);
        graphics.fill(left, top, left + 1, this.height - 6, GOLD_DIM);
        int width = right - left - 20;
        List<Line> lines = new ArrayList<>();
        wrap(lines, quest.title(), GOLD, 0, width, null);
        String status = STATUS_NAME[Mth.clamp(quest.status(), 0, 5)] + (quest.optional() ? "  (optional)" : "");
        if (quest.secondsLeft() >= 0) {
            status += "  " + quest.secondsLeft() / 60 + ":" + String.format("%02d", quest.secondsLeft() % 60) + " left";
        }
        wrap(lines, Component.literal(status), STATUS_COLOR[Mth.clamp(quest.status(), 0, 5)], 0, width, null);
        lines.add(null);
        wrap(lines, quest.description(), TEXT, 0, width, null);
        if (!quest.objectives().isEmpty()) {
            lines.add(null);
            wrap(lines, Component.literal("Objectives"), MUTED, 0, width, null);
            for (ObjectiveView objective : quest.objectives()) {
                boolean done = objective.progress() >= objective.count();
                boolean tickable = objective.checkmark() && !done && quest.status() == QuestView.ACTIVE;
                String suffix = objective.count() > 1 ? "  " + objective.progress() + "/" + objective.count() : "";
                Component text = Component.literal(done ? "[x] " : tickable ? "[ ] " : " -  ").append(objective.text())
                        .append(suffix + (objective.optional() ? " (optional)" : "") + (tickable ? "  (click)" : ""));
                String questId = quest.id().toString();
                wrap(lines, text, done ? GOLD_DIM : tickable ? GOLD : TEXT, 4, width, tickable
                        ? () -> QuestNetwork.intent(QuestNetwork.CHECK, questId, objective.id(), 0, 0, 0) : null);
            }
        }
        if (!quest.rewards().isEmpty()) {
            lines.add(null);
            wrap(lines, Component.literal("Rewards"), MUTED, 0, width, null);
            for (RewardView reward : quest.rewards()) {
                wrap(lines, Component.literal(reward.claimed() ? "[x] " : " +  ").append(reward.text()),
                        reward.claimed() ? GOLD_DIM : TEXT, 4, width, null);
            }
        }
        if (!quest.story().isEmpty()) {
            lines.add(null);
            wrap(lines, Component.literal("Story so far"), MUTED, 0, width, null);
            for (Component story : quest.story()) {
                wrap(lines, story.copy().withStyle(ChatFormatting.ITALIC), 0xFFAAAACC, 4, width, null);
            }
        }
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, lines.size() * 11 - (bottom - top - 16)));
        graphics.enableScissor(left, top + 4, right, bottom);
        draw(graphics, lines, left + 10, top + 10 - (int) detailScroll, top, bottom);
        graphics.disableScissor();
    }

    private void wrap(List<Line> lines, Component text, int color, int indent, int width, Runnable click) {
        for (FormattedCharSequence line : this.font.split(text, Math.max(40, width - indent))) {
            lines.add(new Line(line, color, indent, click));
        }
    }

    /** Draws lines 11px apart (null = blank line) and registers the clickable ones. */
    private void draw(GuiGraphics graphics, List<Line> lines, int x, int y, int clipTop, int clipBottom) {
        for (Line line : lines) {
            if (line != null && y > clipTop - 11 && y < clipBottom) {
                graphics.drawString(this.font, line.text, x + line.indent, y, line.color, false);
                if (line.click != null && y >= clipTop && y + 10 <= clipBottom) {
                    hits.add(new Hit(x, y - 1, x + line.indent + this.font.width(line.text), y + 10, line.click));
                }
            }
            y += 11;
        }
    }

    private List<Line> journal() {
        int width = this.width - 40;
        List<Line> lines = new ArrayList<>();
        String[] titles = {"Active", "Completed", "Failed"};
        int[][] statuses = {{QuestView.ACTIVE}, {QuestView.COMPLETED, QuestView.CLAIMABLE}, {QuestView.FAILED}};
        for (int group = 0; group < 3; group++) {
            wrap(lines, Component.literal(titles[group]), GOLD, 0, width, null);
            int count = 0;
            for (QuestView quest : ClientQuests.QUESTS.values()) {
                if (quest.status() != statuses[group][0] && quest.status() != statuses[group][statuses[group].length - 1]) {
                    continue;
                }
                count++;
                if (group == 0) {
                    // Clicking an active quest toggles it in the HUD tracker.
                    String id = quest.id().toString();
                    boolean tracked = quest.tracked();
                    wrap(lines, Component.literal(tracked ? "[tracked] " : "[track] ").append(quest.title()),
                            tracked ? GOLD : TEXT, 6, width,
                            () -> QuestNetwork.intent(QuestNetwork.TRACK, id, "", tracked ? 0 : 1, 0, 0));
                    for (Component story : quest.story()) {
                        wrap(lines, story.copy().withStyle(ChatFormatting.ITALIC), 0xFFAAAACC, 18, width, null);
                    }
                } else {
                    wrap(lines, quest.title(), group == 1 ? GOLD_DIM : 0xFFAA4444, 6, width, null);
                }
            }
            if (count == 0) {
                wrap(lines, Component.literal("Nothing here."), MUTED, 6, width, null);
            }
            lines.add(null);
        }
        wrap(lines, Component.literal("Conversations"), GOLD, 0, width, null);
        if (ClientQuests.log.isEmpty()) {
            wrap(lines, Component.literal("Nothing here."), MUTED, 6, width, null);
        }
        for (Component line : ClientQuests.log) {
            wrap(lines, line, TEXT, 6, width, null);
        }
        return lines;
    }

    private void renderLines(GuiGraphics graphics, List<Line> lines, int left, int right) {
        int top = TOP + 4, bottom = this.height - 10;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() * 11 - (bottom - top - 12)));
        graphics.enableScissor(left, top, right, bottom);
        draw(graphics, lines, left + 4, top + 6 - (int) scroll, top, bottom);
        graphics.disableScissor();
    }

    private void renderFactions(GuiGraphics graphics) {
        int y = TOP + 12 - (int) scroll;
        int left = Math.max(20, this.width / 2 - 170), right = Math.min(this.width - 20, this.width / 2 + 170);
        if (ClientQuests.factions.isEmpty()) {
            graphics.drawCenteredString(this.font, "No factions yet.", this.width / 2, this.height / 2, MUTED);
        }
        for (FactionView faction : ClientQuests.factions) {
            int color = 0xFF000000 | faction.color();
            graphics.fill(left, y, right, y + 34, 0xC00C0C1C);
            graphics.fill(left, y, left + 2, y + 34, color);
            int textX = ClientQuests.icon(graphics, faction.icon(), left + 8, y + 9) ? left + 30 : left + 10;
            graphics.drawString(this.font, faction.name(), textX, y + 6, color, false);
            String standing = faction.rank().getString() + "  " + faction.value();
            graphics.drawString(this.font, standing, right - 8 - this.font.width(standing), y + 6, TEXT, false);
            // Bar: progress from the start of this rank to the start of the next.
            int barLeft = textX, barRight = right - 8;
            double span = Math.max(1, faction.next() - faction.floor());
            double filled = Mth.clamp((faction.value() - faction.floor()) / span, 0, 1);
            graphics.fill(barLeft, y + 22, barRight, y + 26, 0xFF16162A);
            graphics.fill(barLeft, y + 22, barLeft + (int) ((barRight - barLeft) * filled), y + 26, color);
            y += 40;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (mouseX >= hit.x0 && mouseX < hit.x1 && mouseY >= hit.y0 && mouseY < hit.y1) {
                hit.action.run();
                return true;
            }
        }
        dragging = tab == Tab.QUESTS && mouseX > LEFT && mouseX < canvasRight() && mouseY > TOP;
        return dragging;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab != Tab.QUESTS) {
            scroll -= delta * 22;
        } else if (detailsOpen() && mouseX >= this.width - RIGHT) {
            detailScroll -= delta * 22;
        } else if (mouseX > LEFT) {
            zoom = Mth.clamp(zoom + delta * 0.15, 0.5, 2.0);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
