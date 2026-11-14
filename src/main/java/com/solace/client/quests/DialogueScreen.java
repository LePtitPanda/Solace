package com.solace.client.quests;

import com.solace.client.gui.WorldCreationThemeRenderer;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.network.Views.ChoiceView;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation panel: letterboxed, speaker plate, typewriter text, choices stacked on the
 * right. It only shows what the server sent and sends back which choice was picked.
 */
public class DialogueScreen extends Screen {
    private static final int PANEL_HEIGHT = 92;
    private static final int MARGIN = 24;
    private static final int CHOICE_HEIGHT = 22;
    private static final int CHOICE_WIDTH = 230;
    private static final int PANEL_FILL = 0xE60C0C1C;
    private static final long MILLIS_PER_CHAR = 18L;

    private QuestNetwork.DialogueNode node;
    private List<FormattedCharSequence> lines = List.of();
    private int totalChars;
    private long shownAt;
    private boolean revealAll;
    private boolean closed;
    private final ChibiPortrait portrait = new ChibiPortrait();

    public DialogueScreen(QuestNetwork.DialogueNode node) {
        super(Component.literal("Dialogue"));
        this.node = node;
        this.shownAt = Util.getMillis();
    }

    public int session() {
        return node.session();
    }

    /** A new node of the same or a follow-up conversation. */
    public void show(QuestNetwork.DialogueNode next) {
        this.node = next;
        this.shownAt = Util.getMillis();
        this.revealAll = false;
        layout();
    }

    public void closedByServer() {
        closed = true;
        this.minecraft.setScreen(null);
    }

    @Override
    protected void init() {
        layout();
    }

    private int textLeft() {
        return MARGIN + (hasPortrait() ? 70 : 14);
    }

    private boolean hasPortrait() {
        return node.npcEntity() >= 0 || !node.item().isEmpty();
    }

    private void layout() {
        if (this.font == null) {
            return;
        }
        lines = this.font.split(node.text(), Math.max(60, this.width - MARGIN - 14 - textLeft()));
        totalChars = 0;
        for (FormattedCharSequence line : lines) {
            totalChars += length(line);
        }
    }

    private static int length(FormattedCharSequence line) {
        int[] count = {0};
        line.accept((index, style, codePoint) -> {
            count[0]++;
            return true;
        });
        return count[0];
    }

    private static FormattedCharSequence limit(FormattedCharSequence line, int chars) {
        return sink -> {
            int[] left = {chars};
            return line.accept((index, style, codePoint) -> left[0]-- > 0 && sink.accept(index, style, codePoint));
        };
    }

    private int revealed() {
        return revealAll ? totalChars : (int) Math.min(totalChars, (Util.getMillis() - shownAt) / MILLIS_PER_CHAR);
    }

    private boolean textDone() {
        return revealed() >= totalChars;
    }

    private List<int[]> choiceBoxes() {
        // x, y for each choice: stacked upward from just above the panel, on the right.
        List<int[]> boxes = new ArrayList<>();
        int count = node.choices().size();
        int bottom = this.height - PANEL_HEIGHT - MARGIN - 8;
        for (int i = 0; i < count; i++) {
            boxes.add(new int[]{this.width - MARGIN - CHOICE_WIDTH, bottom - (count - i) * (CHOICE_HEIGHT + 4)});
        }
        return boxes;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Letterbox bars instead of a full backdrop: the world and the NPC stay visible.
        graphics.fill(0, 0, this.width, 18, 0xFF000000);
        graphics.fill(0, this.height - 10, this.width, this.height, 0xFF000000);

        int left = MARGIN;
        int right = this.width - MARGIN;
        int top = this.height - PANEL_HEIGHT - MARGIN;
        int bottom = this.height - MARGIN;
        graphics.fill(left, top, right, bottom, PANEL_FILL);
        graphics.fill(left, top, right, top + 1, WorldCreationThemeRenderer.GOLD);
        graphics.fill(left, bottom - 1, right, bottom, WorldCreationThemeRenderer.GOLD_DIM);

        String speaker = node.speaker().getString();
        if (!speaker.isEmpty()) {
            int plateWidth = this.font.width(node.speaker()) + 18;
            graphics.fill(left + 10, top - 15, left + 10 + plateWidth, top + 1, 0xFF0C0C1C);
            graphics.fill(left + 10, top - 15, left + 10 + plateWidth, top - 14, WorldCreationThemeRenderer.GOLD);
            graphics.drawString(this.font, node.speaker(), left + 19, top - 10, WorldCreationThemeRenderer.GOLD, false);
        }

        if (node.npcEntity() >= 0 && this.minecraft.level != null) {
            Entity entity = this.minecraft.level.getEntity(node.npcEntity());
            if (entity instanceof LivingEntity living) {
                portrait.draw(graphics, living, left + 38, (top + bottom) / 2, PANEL_HEIGHT - 12);
            }
        } else if (!node.item().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(left + 14, top + 22, 0);
            graphics.pose().scale(3.0F, 3.0F, 1.0F);
            ClientQuests.icon(graphics, "item:" + node.item(), 0, 0);
            graphics.pose().popPose();
        }

        int budget = revealed();
        int y = top + 14;
        for (FormattedCharSequence line : lines) {
            if (budget <= 0) {
                break;
            }
            int chars = length(line);
            graphics.drawString(this.font, budget >= chars ? line : limit(line, budget), textLeft(), y,
                    WorldCreationThemeRenderer.TEXT, false);
            budget -= chars;
            y += this.font.lineHeight + 3;
        }

        if (textDone()) {
            List<int[]> boxes = choiceBoxes();
            for (int i = 0; i < boxes.size(); i++) {
                ChoiceView choice = node.choices().get(i);
                int x = boxes.get(i)[0];
                int boxY = boxes.get(i)[1];
                boolean hovered = choice.enabled() && mouseX >= x && mouseX < x + CHOICE_WIDTH && mouseY >= boxY
                        && mouseY < boxY + CHOICE_HEIGHT;
                graphics.fill(x, boxY, x + CHOICE_WIDTH, boxY + CHOICE_HEIGHT, hovered ? 0xF01A1508 : PANEL_FILL);
                graphics.fill(x, boxY, x + 2, boxY + CHOICE_HEIGHT, choice.enabled()
                        ? WorldCreationThemeRenderer.GOLD : WorldCreationThemeRenderer.GOLD_DIM);
                String number = (i + 1) + ".";
                graphics.drawString(this.font, number, x + 8, boxY + 7, WorldCreationThemeRenderer.GOLD_DIM, false);
                List<FormattedCharSequence> label = this.font.split(choice.text(), CHOICE_WIDTH - 30);
                if (!label.isEmpty()) {
                    graphics.drawString(this.font, label.get(0), x + 22, boxY + 7, !choice.enabled()
                            ? WorldCreationThemeRenderer.MUTED : hovered ? WorldCreationThemeRenderer.GOLD
                            : WorldCreationThemeRenderer.TEXT, false);
                }
            }
            if (node.choices().isEmpty() && node.canAdvance() && (Util.getMillis() / 500) % 2 == 0) {
                graphics.drawString(this.font, ">", right - 14, bottom - 14, WorldCreationThemeRenderer.GOLD, false);
            }
        }
    }

    private void pick(int position) {
        if (position < 0 || position >= node.choices().size() || !node.choices().get(position).enabled()) {
            return;
        }
        QuestNetwork.intent(QuestNetwork.DIALOGUE_CHOICE, "", "", node.session(), node.sequence(),
                node.choices().get(position).index());
    }

    /** First press finishes the typewriter; the next one continues. */
    private void proceed() {
        if (!textDone()) {
            revealAll = true;
        } else if (node.choices().isEmpty() && node.canAdvance()) {
            QuestNetwork.intent(QuestNetwork.DIALOGUE_ADVANCE, "", "", node.session(), node.sequence(), 0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (textDone()) {
            List<int[]> boxes = choiceBoxes();
            for (int i = 0; i < boxes.size(); i++) {
                int[] box = boxes.get(i);
                if (mouseX >= box[0] && mouseX < box[0] + CHOICE_WIDTH && mouseY >= box[1]
                        && mouseY < box[1] + CHOICE_HEIGHT) {
                    pick(i);
                    return true;
                }
            }
        }
        proceed();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            proceed();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            if (textDone()) {
                pick(keyCode - GLFW.GLFW_KEY_1);
            } else {
                revealAll = true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // Esc: tell the server, so the NPC is released at once.
        if (!closed) {
            closed = true;
            QuestNetwork.intent(QuestNetwork.DIALOGUE_CLOSE, "", "", node.session(), 0, 0);
        }
        super.onClose();
    }

    @Override
    public void removed() {
        portrait.close();
        // Replaced by another screen (death, inventory...): same as closing.
        if (!closed) {
            closed = true;
            QuestNetwork.intent(QuestNetwork.DIALOGUE_CLOSE, "", "", node.session(), 0, 0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
