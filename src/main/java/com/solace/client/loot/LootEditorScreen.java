package com.solace.client.loot;

import com.solace.loot.LootEditorEntry;
import com.solace.loot.LootEditorSnapshot;
import com.solace.menu.LootEditorMenu;
import com.solace.network.LootEditorNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class LootEditorScreen extends AbstractContainerScreen<LootEditorMenu> {
    private EditBox replacement;
    private int selected = -1;

    public LootEditorScreen(LootEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 480;
        imageHeight = 260;
        inventoryLabelY = 143;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = leftPos + 250;

        addRenderableWidget(Button.builder(Component.literal("-"), button -> adjust(-1))
                .bounds(panelX, topPos + 62, 34, 22).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> adjust(1))
                .bounds(panelX + 40, topPos + 62, 34, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), button -> removeSelected())
                .bounds(panelX + 80, topPos + 62, 134, 22).build());

        replacement = new EditBox(font, panelX, topPos + 124, 214, 20, Component.literal("Loot table id"));
        replacement.setMaxLength(256);
        replacement.setValue(menu.snapshot().effective().toString());
        addRenderableWidget(replacement);
        addRenderableWidget(Button.builder(Component.literal("Use table"), button -> LootEditorNetwork.changeTable(replacement.getValue()))
                .bounds(panelX, topPos + 148, 104, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Reset default"), button -> menuButton(LootEditorMenu.RESET))
                .bounds(panelX + 110, topPos + 148, 104, 22).build());

        addRenderableWidget(Button.builder(Component.literal("<"), button -> menuButton(LootEditorMenu.PREVIOUS_PAGE))
                .bounds(panelX, topPos + 176, 34, 22).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> menuButton(LootEditorMenu.NEXT_PAGE))
                .bounds(panelX + 40, topPos + 176, 34, 22).build());
        addRenderableWidget(Button.builder(Component.literal(menu.snapshot().suppressModifiers() ? "Extras OFF" : "Extras ON"),
                        button -> menuButton(LootEditorMenu.TOGGLE_MODIFIERS))
                .bounds(panelX + 80, topPos + 176, 78, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Index"), button -> menuButton(LootEditorMenu.REBUILD_INDEX))
                .bounds(panelX + 164, topPos + 176, 50, 22).build());
        String structureLabel = menu.snapshot().structureIndexReady()
                ? "Structures (" + menu.snapshot().structures().size() + ")"
                : "Structures (indexing...)";
        addRenderableWidget(Button.builder(Component.literal(structureLabel), button -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new LootStructureScreen(this, menu.snapshot()));
                    }
                })
                .bounds(panelX, topPos + 204, 214, 22).build());
    }

    private void adjust(int direction) {
        if (selected < 0 || menu.entry(selected) == null) return;
        menuButton((direction < 0 ? LootEditorMenu.DECREMENT_BASE : LootEditorMenu.INCREMENT_BASE) + selected);
    }

    private void removeSelected() {
        if (selected >= 0 && menu.entry(selected) != null) {
            menuButton(LootEditorMenu.REMOVE_BASE + selected);
        }
    }

    private void menuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0101010);
        graphics.fill(leftPos + 242, topPos + 34, leftPos + 244, topPos + imageHeight - 6, 0xFF606060);
        for (int i = 0; i <= LootEditorMenu.ADD_SLOT; i++) {
            Slot slot = menu.getSlot(i);
            LootEditorEntry entry = i < LootEditorMenu.ENTRY_SLOTS ? menu.entry(i) : null;
            int color = i == selected ? 0xFFFFFF55
                    : i == LootEditorMenu.ADD_SLOT ? 0xFF55AA55
                    : entry != null && entry.external() ? 0xFFAA55FF : 0xFF777777;
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                    leftPos + slot.x + 17, topPos + slot.y + 17, color);
            graphics.fill(leftPos + slot.x, topPos + slot.y,
                    leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF202020);
        }
        for (int row = 0; row < 4; row++) {
            int y = row == 3 ? 226 : 154 + row * 18;
            for (int column = 0; column < 9; column++) {
                int x = 8 + column * 18;
                graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, 0xFF666666);
                graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xFF202020);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        LootEditorSnapshot data = menu.snapshot();
        graphics.drawString(font, trim("Source: " + data.source(), 464), 8, 9, 0xFFFFFF, false);
        if (!data.source().equals(data.effective())) {
            graphics.drawString(font, trim("Using: " + data.effective(), 464), 8, 22, 0xFFD47A, false);
        }
        graphics.drawString(font, "Cursor > slot: replace", 8, 110, 0xB0B0B0, false);
        graphics.drawString(font, "Green + / shift-click: add", 8, 122, 0xB0B0B0, false);
        graphics.drawString(font, "Purple slots: mod-injected extras", 8, 134, 0xD695FF, false);

        for (int i = 0; i < LootEditorMenu.ENTRY_SLOTS; i++) {
            int weight = menu.weight(i);
            LootEditorEntry entry = menu.entry(i);
            if (entry != null && (weight > 0 || entry.external())) {
                Slot slot = menu.getSlot(i);
                String value = entry.external() ? compactChance(weight) : Integer.toString(weight);
                graphics.drawString(font, value, slot.x + 1, slot.y + 12,
                        entry.external() ? 0xD695FF : 0xFFFF55, true);
            }
        }
        graphics.drawString(font, "+", 225, 68, 0x55FF55, true);

        int panelX = 250;
        LootEditorEntry selectedEntry = selected >= 0 ? menu.entry(selected) : null;
        graphics.drawString(font, selectedEntry != null && selectedEntry.external()
                ? "Injected chance (this table only)" : "Selected item weight", panelX, 46, 0xFFFFFF, false);
        String selectedText = "Click an entry slot";
        if (selectedEntry != null) {
            LootEditorEntry entry = selectedEntry;
            int currentWeight = menu.weight(selected);
            if (entry.external()) {
                selectedText = entry.stack().getHoverName().getString() + ": "
                        + (currentWeight < 0 ? "unknown; edit to take control" : formatChance(currentWeight));
            } else {
                int adjustedPoolWeight = Math.max(1, entry.poolWeight() - entry.weight() + currentWeight);
                double chance = 100.0D * currentWeight / adjustedPoolWeight;
                selectedText = entry.stack().getHoverName().getString() + ": " + currentWeight
                        + " (~" + String.format(java.util.Locale.ROOT, "%.1f", chance) + "%)";
            }
        }
        graphics.drawString(font, trim(selectedText, 214), panelX, 92, 0xC8C8C8, false);
        graphics.drawString(font, "Replace whole table with:", panelX, 112, 0xFFFFFF, false);
        String pageText = "Page " + (data.page() + 1) + "/" + data.pageCount()
                + " (" + data.directEntries() + " table + " + data.externalEntries() + " extras)";
        graphics.drawString(font, trim(pageText, 214), panelX, 230, 0xFFFFFF, false);
        String status = data.overridden() ? "Saved modpack override" : "Showing datapack default";
        status += data.suppressModifiers() ? "; extras off" : "; extras on";
        graphics.drawString(font, trim(status, 214), panelX, 244,
                data.overridden() ? 0x80FF80 : 0xB0B0B0, false);
    }

    private String trim(String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }

    private static String compactChance(int basisPoints) {
        if (basisPoints < 0) return "?";
        if (basisPoints == 10_000) return "100";
        if (basisPoints % 100 == 0) return Integer.toString(basisPoints / 100);
        return String.format(java.util.Locale.ROOT, "%.1f", basisPoints / 100.0D);
    }

    private static String formatChance(int basisPoints) {
        if (basisPoints % 100 == 0) return (basisPoints / 100) + "%";
        return String.format(java.util.Locale.ROOT, "%.2f%%", basisPoints / 100.0D);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 330 && mouseX <= leftPos + 408
                && mouseY >= topPos + 176 && mouseY <= topPos + 198) {
            graphics.renderTooltip(font, Component.literal(menu.snapshot().suppressModifiers()
                    ? "Mod-added loot is blocked for this table."
                    : "Mods may inject extra items after this table rolls."), mouseX, mouseY);
        } else if (mouseX >= leftPos + 414 && mouseX <= leftPos + 464
                && mouseY >= topPos + 176 && mouseY <= topPos + 198) {
            graphics.renderTooltip(font, Component.literal("Rebuild structure and mod-extra indexes."), mouseX, mouseY);
        } else if (mouseX >= leftPos + 360 && mouseX <= leftPos + 464
                && mouseY >= topPos + 148 && mouseY <= topPos + 170) {
            graphics.renderTooltip(font, Component.literal("Reset this table's base edits, extra rules, and Extras toggle."),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < LootEditorMenu.ENTRY_SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            if (menu.entry(i) != null && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)
                    && menu.getCarried().isEmpty()) {
                selected = i;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (int i = 0; i < LootEditorMenu.ENTRY_SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            if (menu.entry(i) != null && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                selected = i;
                adjust(delta < 0 ? -1 : 1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
