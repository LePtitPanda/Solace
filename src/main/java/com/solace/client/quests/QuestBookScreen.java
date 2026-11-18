package com.solace.client.quests;

import com.solace.client.gui.SolaceArt;
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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.solace.client.gui.WorldCreationThemeRenderer.LIGHT;
import static com.solace.client.gui.WorldCreationThemeRenderer.LINE;
import static com.solace.client.gui.WorldCreationThemeRenderer.MUTED;
import static com.solace.client.gui.WorldCreationThemeRenderer.PANEL;
import static com.solace.client.gui.WorldCreationThemeRenderer.TEXT;

/**
 * The quest book: chapters on the left, a pannable and zoomable quest graph in the middle, the
 * selected quest's details on the right; plus a Factions tab. Purely a view of
 * {@link ClientQuests}; every button sends an intent the server re-checks.
 */
public class QuestBookScreen extends Screen {
    private enum Tab { QUESTS, FACTIONS }

    private static final int MIN_LEFT = 120, MAX_LEFT = 320;
    private static final int RIGHT = 210;
    private static final int TOP = 36;
    private static final int GRID = 46;
    private static final int NODE = 26;
    // In progress is blue, completed is green: easy to tell apart at a glance.
    // The UI's own greys: in progress is lit white, completed is quiet grey with a check, failed is a dull red.
    private static final int[] STATUS_COLOR = {0xFF3A3A3A, TEXT, SolaceArt.WHITE, SolaceArt.WHITE, LINE, 0xFFA05050};
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
    private boolean resizing;

    /** Width of the chapter list; dragged with its right edge and kept in the client config. */
    private static int left() {
        return Mth.clamp(QuestClientConfig.get().bookSidebar, MIN_LEFT, MAX_LEFT);
    }
    private String builtFor = "";
    /** Screen rows of clickable things drawn this frame: x0, y0, x1, y1 and what a click does. */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
    }

    private record Line(FormattedCharSequence text, int color, int indent, Runnable click) {
    }

    /** Item slots drawn this frame (quest details), for tooltips and JEI/EMI lookups. */
    private record ItemSlot(int x, int y, ItemStack stack) {
    }

    private final List<ItemSlot> itemSlots = new ArrayList<>();

    /** The required item under the mouse in an open quest book, or empty: JEI and EMI ask this for R/U. */
    public static ItemStack stackAt(net.minecraft.client.gui.screens.Screen screen, double mouseX, double mouseY) {
        if (screen instanceof QuestBookScreen book) {
            for (ItemSlot slot : book.itemSlots) {
                if (mouseX >= slot.x && mouseX < slot.x + 18 && mouseY >= slot.y && mouseY < slot.y + 18) return slot.stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * JEI and EMI only read keys and clicks on inventory screens, so the book opens their views itself:
     * left click or R shows recipes, right click or U shows uses.
     */
    private boolean showRecipes(double mouseX, double mouseY, boolean uses) {
        ItemStack stack = stackAt(this, mouseX, mouseY);
        if (stack.isEmpty()) return false;
        if (net.minecraftforge.fml.ModList.get().isLoaded("emi")) {
            com.solace.compat.emi.SolaceEmiPlugin.show(stack, uses);
        } else if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
            com.solace.compat.jei.SolaceJeiPlugin.show(stack, uses);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // shortcut: R/U are the JEI/EMI defaults; a rebound key won't work here.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_U) {
            net.minecraft.client.MouseHandler mouse = this.minecraft.mouseHandler;
            double x = mouse.xpos() * this.width / this.minecraft.getWindow().getScreenWidth();
            double y = mouse.ypos() * this.height / this.minecraft.getWindow().getScreenHeight();
            if (showRecipes(x, y, keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_U)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            String name = value == Tab.QUESTS ? "Quests" : "Factions";
            addRenderableWidget(new BookButton(x, 6, 100, 24, Component.literal(name), tab == value, () -> {
                tab = value;
                scroll = 0;
                rebuildWidgets();
            }));
            x += 104;
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
        return (int) ((left() + canvasRight()) / 2.0 + panX + quest.x() * GRID * zoom);
    }

    private int nodeY(QuestView quest) {
        return (int) ((TOP + this.height - 10) / 2.0 + panY + quest.y() * GRID * zoom);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        WorldCreationThemeRenderer.renderBackdrop(this, graphics);
        hits.clear();
        itemSlots.clear();
        // Rule under the tabs: grey line between black outlines.
        graphics.fill(0, TOP - 3, this.width, TOP, SolaceArt.BLACK);
        graphics.fill(0, TOP - 2, this.width, TOP - 1, LINE);
        if (ClientQuests.chapters.isEmpty() && tab == Tab.QUESTS) {
            graphics.drawCenteredString(this.font, "No quests yet.", this.width / 2, this.height / 2, MUTED);
        } else if (tab == Tab.QUESTS) {
            renderChapters(graphics, mouseX, mouseY);
            renderCanvas(graphics, mouseX, mouseY);
            if (detailsOpen()) {
                renderDetails(graphics, quest());
            }
        } else {
            renderFactions(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack under = stackAt(this, mouseX, mouseY);
        if (!under.isEmpty()) graphics.renderTooltip(this.font, under, mouseX, mouseY);
    }

    private void renderChapters(GuiGraphics graphics, int mouseX, int mouseY) {
        ChapterView current = currentChapter();
        int y = TOP + 6;
        for (ChapterView view : ClientQuests.chapters) {
            boolean active = current != null && view.id().equals(current.id());
            boolean hovered = mouseX >= 6 && mouseX < left() - 6 && mouseY >= y && mouseY < y + 22;
            // Chapters are inventory slots; the open one gets a lit frame.
            SolaceArt.slot(graphics::fill, 6, y, left() - 12, 22,
                    active || hovered ? SolaceArt.SLOT_HOVER : SolaceArt.SLOT);
            if (active) {
                SolaceArt.frame(graphics::fill, 6, y, left() - 12, 22, SolaceArt.Tones.HOVER);
            }
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
                    left() - 12 - textX - this.font.width(count) - 6);
            graphics.drawString(this.font, title, textX, y + 7, active ? WorldCreationThemeRenderer.TITLE : TEXT,
                    false);
            graphics.drawString(this.font, count, left() - 10 - this.font.width(count), y + 7, MUTED, false);
            ResourceLocation id = view.id();
            hits.add(new Hit(6, y, left() - 6, y + 22, () -> {
                chapter = id;
                selected = null;
                panX = panY = 0;
                rebuildWidgets();
            }));
            y += 25;
        }
        if (current != null) {
            int textY = y + 8;
            for (FormattedCharSequence line : this.font.split(current.description(), left() - 20)) {
                graphics.drawString(this.font, line, 10, textY, MUTED, false);
                textY += 10;
            }
        }
    }

    private void renderCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = left(), right = canvasRight(), top = TOP, bottom = this.height - 10;
        graphics.fill(left - 2, top + 4, left - 1, bottom - 4, resizing ? LIGHT : SolaceArt.DIM); // sidebar drag edge
        WorldCreationThemeRenderer.renderPanel(graphics, left, top, right - left, bottom - top, 0xB0050505,
                SolaceArt.Tones.NORMAL, SolaceArt.PANEL_ORNATE);
        graphics.enableScissor(left + 3, top + 3, right - 3, bottom - 3);
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
                int color = done ? LINE : 0xFF2E2E2E;
                int x0 = nodeX(required), y0 = nodeY(required), x1 = nodeX(quest), y1 = nodeY(quest);
                // Bend halfway between the columns, so a branch never runs through the nodes of another column.
                int mx = x0 == x1 ? x0 : x0 + (x1 > x0 ? 1 : -1) * (int) (GRID * zoom / 2);
                graphics.fill(Math.min(x0, mx), y0, Math.max(x0, mx) + 1, y0 + 1, color);
                graphics.fill(mx, Math.min(y0, y1), mx + 1, Math.max(y0, y1) + 1, color);
                graphics.fill(Math.min(mx, x1), y1, Math.max(mx, x1) + 1, y1 + 1, color);
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
                color = LINE;
            }
            // Nodes are framed slots; the status color runs along the bottom inside the frame.
            boolean lit = quest.id().equals(selected) || over;
            SolaceArt.slot(graphics::fill, x - half - 2, y - half - 2, half * 2 + 4, half * 2 + 4,
                    quest.status() == QuestView.LOCKED ? 0xFF080808 : SolaceArt.SLOT);
            SolaceArt.frame(graphics::fill, x - half - 2, y - half - 2, half * 2 + 4, half * 2 + 4,
                    lit ? SolaceArt.Tones.HOVER : quest.status() == QuestView.LOCKED
                            ? SolaceArt.Tones.DISABLED : SolaceArt.Tones.NORMAL, false);
            graphics.fill(x - half + 1, y + half - 2, x + half - 1, y + half, color);
            graphics.pose().pushPose();
            graphics.pose().translate(x - 8 * zoom, y - 8 * zoom, 0);
            graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
            if (!ClientQuests.icon(graphics, quest.icon(), 0, 0)) {
                graphics.drawString(this.font, "?", 5, 4, color, false);
            }
            graphics.pose().popPose();
            if (quest.status() == QuestView.COMPLETED) {
                // Finished: the icon is dimmed and carries a small check; in-progress icons stay bright.
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(x - half + 1, y - half + 1, x + half - 1, y + half - 2, 0x90000000);
                graphics.drawString(this.font, "\u2714", x + half - 8, y - half + 1, LIGHT, true);
                graphics.pose().popPose();
            }
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
        WorldCreationThemeRenderer.renderPanel(graphics, left, top, right - left, this.height - 6 - top, 0xF0050505,
                SolaceArt.Tones.NORMAL, SolaceArt.PANEL_ORNATE);
        int width = right - left - 20;
        List<Line> lines = new ArrayList<>();
        wrap(lines, quest.title(), WorldCreationThemeRenderer.TITLE, 0, width, null);
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
                int indent = objective.indent() ? 14 : 4;
                wrap(lines, text, done ? LINE : objective.paused() ? MUTED : tickable ? LIGHT : TEXT, indent, width,
                        tickable && !objective.paused()
                                ? () -> QuestNetwork.intent(QuestNetwork.CHECK, questId, objective.id(), 0, 0, 0) : null);
                if (objective.paused()) {
                    wrap(lines, objective.pausedText(), MUTED, indent + 16, width, null);
                }
            }
        }
        if (!quest.rewards().isEmpty()) {
            lines.add(null);
            wrap(lines, Component.literal("Rewards"), MUTED, 0, width, null);
            for (RewardView reward : quest.rewards()) {
                wrap(lines, Component.literal(reward.claimed() ? "[x] " : " +  ").append(reward.text()),
                        reward.claimed() ? LINE : TEXT, 4, width, null);
            }
        }
        if (!quest.story().isEmpty()) {
            lines.add(null);
            wrap(lines, Component.literal("Story so far"), MUTED, 0, width, null);
            for (Component story : quest.story()) {
                wrap(lines, story.copy().withStyle(ChatFormatting.ITALIC), 0xFFAAAAAA, 4, width, null);
            }
        }
        // Items the objectives ask for, as slots along the bottom: hover for the tooltip, R/U for JEI or EMI.
        List<ItemStack> items = new ArrayList<>();
        for (ObjectiveView objective : quest.objectives()) {
            String icon = objective.icon();
            if (icon == null || !icon.startsWith("item:")) continue;
            ResourceLocation itemId = ResourceLocation.tryParse(icon.substring(5));
            net.minecraft.world.item.Item item = itemId == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == net.minecraft.world.item.Items.AIR || items.stream().anyMatch(stack -> stack.is(item))) continue;
            items.add(new ItemStack(item, Math.max(1, Math.min(objective.count(), 999))));
        }
        if (!items.isEmpty()) {
            int perRow = Math.max(1, width / 20);
            int rows = (items.size() + perRow - 1) / perRow;
            int slotsTop = bottom - rows * 20 - 2;
            graphics.fill(left + 8, slotsTop - 4, right - 8, slotsTop - 3, SolaceArt.DIM);
            for (int i = 0; i < items.size(); i++) {
                int sx = left + 10 + (i % perRow) * 20, sy = slotsTop + (i / perRow) * 20;
                SolaceArt.slot(graphics::fill, sx, sy, 18, 18, SolaceArt.SLOT);
                graphics.renderItem(items.get(i), sx + 1, sy + 1);
                graphics.renderItemDecorations(this.font, items.get(i), sx + 1, sy + 1);
                itemSlots.add(new ItemSlot(sx, sy, items.get(i)));
            }
            bottom = slotsTop - 8;
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

    private void renderFactions(GuiGraphics graphics) {
        int y = TOP + 12 - (int) scroll;
        int left = Math.max(20, this.width / 2 - 170), right = Math.min(this.width - 20, this.width / 2 + 170);
        if (ClientQuests.factions.isEmpty()) {
            graphics.drawCenteredString(this.font, "No factions yet.", this.width / 2, this.height / 2, MUTED);
        }
        for (FactionView faction : ClientQuests.factions) {
            int color = 0xFF000000 | faction.color();
            SolaceArt.slot(graphics::fill, left, y, right - left, 34, SolaceArt.SLOT);
            SolaceArt.frame(graphics::fill, left, y, right - left, 34, SolaceArt.Tones.NORMAL.withRivet(color));
            SolaceArt.rivet(graphics::fill, left + 2, y + 2, SolaceArt.Tones.NORMAL.withRivet(color));
            int textX = ClientQuests.icon(graphics, faction.icon(), left + 8, y + 9) ? left + 30 : left + 10;
            graphics.drawString(this.font, faction.name(), textX, y + 6, color, false);
            String standing = faction.rank().getString() + "  " + faction.value();
            graphics.drawString(this.font, standing, right - 8 - this.font.width(standing), y + 6, TEXT, false);
            // Bar: progress from the start of this rank to the start of the next.
            int barLeft = textX, barRight = right - 8;
            double span = Math.max(1, faction.next() - faction.floor());
            double filled = Mth.clamp((faction.value() - faction.floor()) / span, 0, 1);
            SolaceArt.slot(graphics::fill, barLeft - 1, y + 21, barRight - barLeft + 2, 6, 0xFF0A0A0A);
            graphics.fill(barLeft, y + 22, barLeft + (int) ((barRight - barLeft) * filled), y + 26, color);
            y += 40;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showRecipes(mouseX, mouseY, button == 1)) {
            return true;
        }
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
        resizing = tab == Tab.QUESTS && mouseY > TOP && Math.abs(mouseX - left()) <= 4;
        if (resizing) {
            return true;
        }
        dragging = tab == Tab.QUESTS && mouseX > left() && mouseX < canvasRight() && mouseY > TOP;
        return dragging;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        if (resizing) {
            resizing = false;
            QuestClientConfig.get().save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizing) {
            QuestClientConfig.get().bookSidebar = Mth.clamp((int) mouseX, MIN_LEFT, MAX_LEFT);
            return true;
        }
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
        } else if (mouseX > left()) {
            zoom = Mth.clamp(zoom + delta * 0.15, 0.5, 2.0);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
