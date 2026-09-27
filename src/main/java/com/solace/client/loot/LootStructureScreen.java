package com.solace.client.loot;

import com.solace.loot.LootEditorSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Full-size, scrollable view of every structure template associated with a loot table. */
public final class LootStructureScreen extends Screen {
    private static final int ROW_HEIGHT = 13;
    private final Screen parent;
    private final LootEditorSnapshot snapshot;
    private final List<ResourceLocation> structures;
    private int scroll;

    public LootStructureScreen(Screen parent, LootEditorSnapshot snapshot) {
        super(Component.literal("Loot table structures"));
        this.parent = parent;
        this.snapshot = snapshot;
        this.structures = snapshot.structures();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = 18;
        int right = width - 18;
        int top = 48;
        int bottom = height - 36;

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xE0101010);
        graphics.fill(left, top, right, bottom, 0xE0202020);
        graphics.drawCenteredString(font, "Structures using this loot table", width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font, trim(snapshot.source().toString(), width - 40), width / 2, 24, 0xA0D8FF);
        String count = snapshot.structureIndexReady()
                ? structures.size() + " structure template" + (structures.size() == 1 ? "" : "s")
                : "Structure index is still building";
        graphics.drawCenteredString(font, count, width / 2, 36, 0xB0B0B0);

        int visibleRows = visibleRows();
        graphics.enableScissor(left, top, right, bottom);
        if (structures.isEmpty()) {
            String empty = snapshot.structureIndexReady()
                    ? "No indexed structure templates use this table."
                    : "Reopen this screen after indexing finishes.";
            graphics.drawCenteredString(font, empty, width / 2, top + 12, 0xB0B0B0);
        } else {
            for (int row = 0; row < visibleRows; row++) {
                int index = scroll + row;
                if (index >= structures.size()) break;
                int y = top + 3 + row * ROW_HEIGHT;
                boolean hovered = mouseX >= left + 5 && mouseX < right - 10
                        && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
                if (hovered) {
                    graphics.fill(left + 3, y - 2, right - 9, y + ROW_HEIGHT - 2, 0xFF383838);
                } else if ((index & 1) != 0) {
                    graphics.fill(left + 3, y - 2, right - 9, y + ROW_HEIGHT - 2, 0xFF282828);
                }
                String id = structures.get(index).toString();
                graphics.drawString(font, (index + 1) + ". " + trim(id, right - left - 36), left + 7, y,
                        0xE8E8E8, false);
            }
        }
        graphics.disableScissor();

        if (structures.size() > visibleRows) {
            int trackTop = top + 2;
            int trackBottom = bottom - 2;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(12, trackHeight * visibleRows / structures.size());
            int maxScroll = maxScroll();
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / Math.max(1, maxScroll);
            graphics.fill(right - 7, trackTop, right - 3, trackBottom, 0xFF101010);
            graphics.fill(right - 7, thumbY, right - 3, thumbY + thumbHeight, 0xFF9A9A9A);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        int hoveredRow = (mouseY - top - 1) / ROW_HEIGHT;
        int hoveredIndex = scroll + hoveredRow;
        if (mouseX >= left + 5 && mouseX < right - 10 && mouseY >= top && mouseY < bottom
                && hoveredRow >= 0 && hoveredIndex >= 0 && hoveredIndex < structures.size()) {
            String id = structures.get(hoveredIndex).toString();
            if (font.width((hoveredIndex + 1) + ". " + id) > right - left - 36) {
                graphics.renderTooltip(font, Component.literal(id), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0D) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264) { // Down
            scroll = Math.min(maxScroll(), scroll + 1);
            return true;
        }
        if (keyCode == 265) { // Up
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        if (keyCode == 267) { // Page down
            scroll = Math.min(maxScroll(), scroll + visibleRows());
            return true;
        }
        if (keyCode == 266) { // Page up
            scroll = Math.max(0, scroll - visibleRows());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int visibleRows() {
        return Math.max(1, (height - 36 - 48 - 6) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, structures.size() - visibleRows());
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private String trim(String value, int availableWidth) {
        if (font.width(value) <= availableWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, availableWidth - font.width("..."))) + "...";
    }
}
