package com.solace.client.loot;

import com.solace.loot.LootEditorEntry;
import com.solace.loot.LootEditorPool;
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

import java.util.Locale;

public final class LootEditorScreen extends AbstractContainerScreen<LootEditorMenu> {
    private EditBox replacement;
    private EditBox weight;
    private EditBox chance;
    private EditBox countMin;
    private EditBox countMax;
    private EditBox rollsMin;
    private EditBox rollsMax;
    private EditBox bonusMin;
    private EditBox bonusMax;
    private Button applyEntry;
    private Button removeEntry;
    private Button applyPool;
    private Button previousPool;
    private Button nextPool;
    private Button previousPage;
    private Button nextPage;
    private Button deletePool;
    private int selected = -1;

    public LootEditorScreen(LootEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 720;
        imageHeight = 344;
        inventoryLabelY = 234;
    }

    @Override
    protected void init() {
        super.init();
        int top = topPos + 34;
        previousPool = addRenderableWidget(Button.builder(Component.literal("< Pool"), button -> menuButton(LootEditorMenu.PREVIOUS_POOL))
                .bounds(leftPos + 8, top, 62, 22).build());
        nextPool = addRenderableWidget(Button.builder(Component.literal("Pool >"), button -> menuButton(LootEditorMenu.NEXT_POOL))
                .bounds(leftPos + 74, top, 62, 22).build());
        addRenderableWidget(Button.builder(Component.literal("New pool"), button -> menuButton(LootEditorMenu.ADD_POOL))
                .bounds(leftPos + 140, top, 84, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Guaranteed +"), button -> menuButton(LootEditorMenu.ADD_GUARANTEED_POOL))
                .bounds(leftPos + 250, top, 108, 22).build());
        deletePool = addRenderableWidget(Button.builder(Component.literal("Delete pool"), button -> menuButton(LootEditorMenu.REMOVE_POOL))
                .bounds(leftPos + 362, top, 92, 22).build());
        previousPage = addRenderableWidget(Button.builder(Component.literal("< Page"), button -> menuButton(LootEditorMenu.PREVIOUS_PAGE))
                .bounds(leftPos + 458, top, 64, 22).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal("Page >"), button -> menuButton(LootEditorMenu.NEXT_PAGE))
                .bounds(leftPos + 526, top, 64, 22).build());
        addRenderableWidget(Button.builder(Component.literal(menu.snapshot().suppressModifiers() ? "Extras OFF" : "Extras ON"),
                        button -> menuButton(LootEditorMenu.TOGGLE_MODIFIERS))
                .bounds(leftPos + 594, top, 76, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Index"), button -> menuButton(LootEditorMenu.REBUILD_INDEX))
                .bounds(leftPos + 674, top, 38, 22).build());

        int panelX = leftPos + 250;
        weight = numericBox(panelX, topPos + 92, 78, "Weight");
        chance = numericBox(panelX + 86, topPos + 92, 78, "Chance percent");
        countMin = numericBox(panelX + 172, topPos + 92, 78, "Minimum count");
        countMax = numericBox(panelX + 258, topPos + 92, 78, "Maximum count");
        applyEntry = addRenderableWidget(Button.builder(Component.literal("Apply item"), button -> applyEntry())
                .bounds(panelX + 344, topPos + 92, 108, 20).build());
        removeEntry = addRenderableWidget(Button.builder(Component.literal("Remove item"), button -> removeSelected())
                .bounds(panelX + 344, topPos + 116, 108, 20).build());

        rollsMin = numericBox(panelX, topPos + 184, 78, "Minimum rolls");
        rollsMax = numericBox(panelX + 86, topPos + 184, 78, "Maximum rolls");
        bonusMin = numericBox(panelX + 172, topPos + 184, 78, "Minimum bonus rolls");
        bonusMax = numericBox(panelX + 258, topPos + 184, 78, "Maximum bonus rolls");
        applyPool = addRenderableWidget(Button.builder(Component.literal("Apply pool"), button -> applyPool())
                .bounds(panelX + 344, topPos + 184, 108, 20).build());

        replacement = new EditBox(font, panelX, topPos + 240, 292, 20, Component.literal("Loot table id"));
        replacement.setMaxLength(256);
        replacement.setValue(menu.snapshot().effective().toString());
        addRenderableWidget(replacement);
        addRenderableWidget(Button.builder(Component.literal("Use table"), button -> LootEditorNetwork.changeTable(replacement.getValue()))
                .bounds(panelX + 298, topPos + 240, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset default"), button -> menuButton(LootEditorMenu.RESET))
                .bounds(panelX + 372, topPos + 240, 80, 20).build());

        String structureLabel = menu.snapshot().structureIndexReady()
                ? "Structures (" + menu.snapshot().structures().size() + ")" : "Structures...";
        addRenderableWidget(Button.builder(Component.literal("Reset chest"), button -> menuButton(LootEditorMenu.RESET_CHEST))
                .bounds(panelX, topPos + 266, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal(structureLabel), button -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new LootStructureScreen(this, menu.snapshot()));
                    }
                }).bounds(panelX + 110, topPos + 266, 128, 20).build());

        previousPool.active = menu.snapshot().selectedPool() > 0;
        nextPool.active = menu.snapshot().selectedPool() + 1 < menu.snapshot().viewCount();
        previousPage.active = menu.snapshot().page() > 0;
        nextPage.active = menu.snapshot().page() + 1 < menu.snapshot().pageCount();
        refreshPoolFields();
        clearEntryFields();
    }

    private EditBox numericBox(int x, int y, int width, String narration) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(narration));
        box.setMaxLength(16);
        addRenderableWidget(box);
        return box;
    }

    private void refreshPoolFields() {
        LootEditorPool pool = selectedPool();
        boolean realPool = pool != null;
        deletePool.active = realPool;
        rollsMin.visible = realPool;
        rollsMax.visible = realPool;
        bonusMin.visible = realPool;
        bonusMax.visible = realPool;
        applyPool.visible = realPool;
        applyPool.active = realPool && (pool.rollsEditable() || pool.bonusRollsEditable());
        setBox(rollsMin, realPool ? number(pool.rollsMin()) : "", realPool && pool.rollsEditable());
        setBox(rollsMax, realPool ? number(pool.rollsMax()) : "", realPool && pool.rollsEditable());
        setBox(bonusMin, realPool ? number(pool.bonusRollsMin()) : "", realPool && pool.bonusRollsEditable());
        setBox(bonusMax, realPool ? number(pool.bonusRollsMax()) : "", realPool && pool.bonusRollsEditable());
    }

    private void clearEntryFields() {
        setBox(weight, "", false);
        setBox(chance, "", false);
        setBox(countMin, "", false);
        setBox(countMax, "", false);
        applyEntry.active = false;
        removeEntry.active = false;
    }

    private void selectEntry(int slot) {
        LootEditorEntry entry = menu.entry(slot);
        if (entry == null) {
            return;
        }
        selected = slot;
        setBox(weight, entry.external() ? "-" : Integer.toString(entry.weight()), !entry.external());
        setBox(chance, entry.chanceBasisPoints() < 0 ? "" : chance(entry.chanceBasisPoints()), true);
        setBox(countMin, Integer.toString(entry.countMin()), entry.countEditable());
        setBox(countMax, Integer.toString(entry.countMax()), entry.countEditable() && !entry.external());
        applyEntry.active = true;
        removeEntry.active = true;
    }

    private static void setBox(EditBox box, String value, boolean editable) {
        box.setValue(value);
        box.setEditable(editable);
        box.active = editable;
    }

    private void applyEntry() {
        LootEditorEntry entry = selected >= 0 ? menu.entry(selected) : null;
        if (entry == null) {
            return;
        }
        try {
            int newWeight = entry.external() ? 1 : Math.max(1, Integer.parseInt(weight.getValue().trim()));
            double chancePercent = Math.max(0.0D, Math.min(100.0D, Double.parseDouble(chance.getValue().trim())));
            int min = Math.max(1, Integer.parseInt(countMin.getValue().trim()));
            int max = entry.external() ? min : Math.max(1, Integer.parseInt(countMax.getValue().trim()));
            LootEditorNetwork.editEntry(selected, newWeight, (int) Math.round(chancePercent * 100.0D), min, max);
        } catch (NumberFormatException exception) {
            error("Weight and counts must be whole numbers; chance may be decimal.");
        }
    }

    private void applyPool() {
        LootEditorPool pool = selectedPool();
        if (pool == null) {
            return;
        }
        try {
            double newRollsMin = pool.rollsEditable() ? Double.parseDouble(rollsMin.getValue().trim()) : pool.rollsMin();
            double newRollsMax = pool.rollsEditable() ? Double.parseDouble(rollsMax.getValue().trim()) : pool.rollsMax();
            double newBonusMin = pool.bonusRollsEditable() ? Double.parseDouble(bonusMin.getValue().trim()) : pool.bonusRollsMin();
            double newBonusMax = pool.bonusRollsEditable() ? Double.parseDouble(bonusMax.getValue().trim()) : pool.bonusRollsMax();
            LootEditorNetwork.editPool(newRollsMin, newRollsMax, newBonusMin, newBonusMax);
        } catch (NumberFormatException exception) {
            error("Pool roll values must be numbers.");
        }
    }

    private void removeSelected() {
        if (selected >= 0 && menu.entry(selected) != null) {
            menuButton(LootEditorMenu.REMOVE_ENTRY_BASE + selected);
        }
    }

    private void error(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private LootEditorPool selectedPool() {
        int index = menu.snapshot().selectedPool();
        return index >= 0 && index < menu.snapshot().pools().size() ? menu.snapshot().pools().get(index) : null;
    }

    private void menuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0101010);
        graphics.fill(leftPos + 242, topPos + 62, leftPos + 244, topPos + imageHeight - 6, 0xFF606060);
        for (int i = 0; i <= LootEditorMenu.ADD_SLOT; i++) {
            Slot slot = menu.getSlot(i);
            LootEditorEntry entry = i < LootEditorMenu.ENTRY_SLOTS ? menu.entry(i) : null;
            int color = i == selected ? 0xFFFFFF55
                    : i == LootEditorMenu.ADD_SLOT ? (menu.snapshot().showingExtras() ? 0xFF444444 : 0xFF55AA55)
                    : entry != null && entry.external() && entry.chanceBasisPoints() == 0 ? 0xFFFF5555
                    : entry != null && entry.external() ? 0xFFAA55FF : 0xFF777777;
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                    leftPos + slot.x + 17, topPos + slot.y + 17, color);
            graphics.fill(leftPos + slot.x, topPos + slot.y,
                    leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF202020);
        }
        for (int row = 0; row < 4; row++) {
            int y = row == 3 ? 318 : 246 + row * 18;
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
        graphics.drawString(font, trim("Source: " + data.source(), 704), 8, 8, 0xFFFFFF, false);
        if (!data.source().equals(data.effective())) {
            graphics.drawString(font, trim("Using: " + data.effective(), 704), 8, 20, 0xFFD47A, false);
        }

        String poolTitle;
        if (data.showingExtras()) {
            poolTitle = "Injected extras (" + data.externalEntries() + ")";
        } else if (selectedPool() != null) {
            poolTitle = "Pool " + (data.selectedPool() + 1) + "/" + data.pools().size() + ": " + selectedPool().name();
        } else {
            poolTitle = "No pools yet";
        }
        graphics.drawString(font, trim(poolTitle, 226), 8, 60, data.showingExtras() ? 0xD695FF : 0x80D8FF, false);
        graphics.drawString(font, data.showingExtras() ? "Post-table injections; select one to edit" : "Carry stack > green +: add to this pool", 8, 148,
                data.showingExtras() ? 0xD695FF : 0xB0B0B0, false);
        graphics.drawString(font, data.showingExtras() ? "Purple border = loot injected by another mod"
                : "Carry stack > item: replace (settings preserved)", 8, 160, 0xB0B0B0, false);
        if (!data.showingExtras()) {
            graphics.drawString(font, "+", 228, 101, 0x55FF55, true);
        }
        graphics.drawString(font, "Inventory", 8, 234, 0xB0B0B0, false);

        for (int i = 0; i < LootEditorMenu.ENTRY_SLOTS; i++) {
            LootEditorEntry entry = menu.entry(i);
            if (entry == null) {
                continue;
            }
            if (entry.external()) {
                if (entry.chanceBasisPoints() == 0) {
                    Slot slot = menu.getSlot(i);
                    graphics.drawString(font, "OFF", slot.x - 1, slot.y + 8, 0xFFFF5555, true);
                }
                continue;
            }
            Slot slot = menu.getSlot(i);
            String top = "w" + entry.weight();
            String bottom = entry.countMin() == entry.countMax() ? "x" + entry.countMin()
                    : entry.countMin() + "-" + entry.countMax();
            graphics.drawString(font, top, slot.x, slot.y + 1, 0xFFFF55, true);
            graphics.drawString(font, bottom, slot.x, slot.y + 12, 0x55FFFF, true);
        }

        int panelX = 250;
        graphics.drawString(font, "Selected entry", panelX, 64, 0xFFFFFF, false);
        graphics.drawString(font, "Weight", panelX, 80, 0xFFFF55, false);
        graphics.drawString(font, "Chance gate %", panelX + 86, 80, 0xFFAAFF, false);
        graphics.drawString(font, "Count min", panelX + 172, 80, 0x55FFFF, false);
        graphics.drawString(font, "Count max", panelX + 258, 80, 0x55FFFF, false);

        LootEditorEntry entry = selected >= 0 ? menu.entry(selected) : null;
        if (entry == null) {
            graphics.drawString(font, "Click an item. Weight only competes inside this pool.", panelX, 140, 0xB0B0B0, false);
        } else if (entry.external()) {
            graphics.drawString(font, trim(entry.stack().getHoverName().getString() + ": post-roll chance "
                    + (entry.chanceBasisPoints() < 0 ? "unknown (enter a value to take control)"
                    : chance(entry.chanceBasisPoints()) + "%"), 446), panelX, 140, 0xD695FF, false);
            graphics.drawString(font, "Injected extras support a constant count; weight does not apply.", panelX, 152, 0xB0B0B0, false);
        } else {
            String selection = entry.direct() && entry.poolWeight() > 0
                    ? String.format(Locale.ROOT, "%.2f%%", 100.0D * entry.weight() / entry.poolWeight())
                    : "advanced/variable";
            double baseChance = entry.direct() && entry.poolWeight() > 0
                    ? (100.0D * entry.weight() / entry.poolWeight()) * entry.chanceBasisPoints() / 10_000.0D : -1.0D;
            String detail = "Selection " + selection + " per roll; chance gate " + chance(entry.chanceBasisPoints()) + "%";
            if (baseChance >= 0.0D) {
                detail += "; base chance " + String.format(Locale.ROOT, "%.2f%%", baseChance);
            }
            graphics.drawString(font, trim(detail, 446), panelX, 140, 0xC8C8C8, false);
            String preserved = (entry.otherConditions() > 0 ? entry.otherConditions() + " other condition(s); " : "")
                    + (entry.otherFunctions() > 0 ? entry.otherFunctions() + " other function(s) preserved" : "no other functions");
            if (!entry.countEditable()) {
                preserved += "; advanced count preserved/read-only";
            }
            graphics.drawString(font, trim(preserved, 446), panelX, 152, 0xB0B0B0, false);
        }

        LootEditorPool pool = selectedPool();
        if (pool != null) {
            graphics.drawString(font, "Pool generation", panelX, 162, 0xFFFFFF, false);
            graphics.drawString(font, "Rolls min", panelX, 172, 0x80D8FF, false);
            graphics.drawString(font, "Rolls max", panelX + 86, 172, 0x80D8FF, false);
            graphics.drawString(font, "Bonus min", panelX + 172, 172, 0x80D8FF, false);
            graphics.drawString(font, "Bonus max", panelX + 258, 172, 0x80D8FF, false);
            String poolInfo = pool.itemEntries() + " visible item entries, " + pool.otherEntries() + " other top-level entries";
            if (pool.conditions() > 0) {
                poolInfo += ", " + pool.conditions() + " pool condition(s)";
            }
            if (!pool.rollsEditable() || !pool.bonusRollsEditable()) {
                poolInfo += "; advanced provider preserved/read-only";
            }
            graphics.drawString(font, trim(poolInfo, 446), panelX, 210, 0xB0B0B0, false);
            graphics.drawString(font, "Each roll makes another selection, so an item may appear repeatedly.", panelX, 222, 0xB0B0B0, false);
        } else {
            graphics.drawString(font, "Injected extra settings", panelX, 174, 0xD695FF, false);
            graphics.drawString(font, "These items are added after the normal loot table finishes.", panelX, 190, 0xB0B0B0, false);
            graphics.drawString(font, "Chance 0% disables one; 100% always adds it. Count is constant.", panelX, 202, 0xB0B0B0, false);
            graphics.drawString(font, "There are no weights or pool rolls for detected extras.", panelX, 214, 0xB0B0B0, false);
        }

        graphics.drawString(font, "Replace whole table with:", panelX, 228, 0xFFFFFF, false);
        String page = "Entry page " + (data.page() + 1) + "/" + data.pageCount() + " (" + data.totalEntries() + " in this view)";
        graphics.drawString(font, page, panelX + 244, 270, 0xFFFFFF, false);
        String status = data.overridden() ? "Saved modpack override" : "Showing datapack default";
        status += data.suppressModifiers() ? "; injected extras blocked" : "; injected extras enabled";
        graphics.drawString(font, trim(status, 446), panelX, 294,
                data.overridden() ? 0x80FF80 : 0xB0B0B0, false);
        graphics.drawString(font, "Table entries: " + data.directEntries() + " | Detected extras: " + data.externalEntries(),
                panelX, 306, 0xB0B0B0, false);
    }

    private String trim(String value, int width) {
        return font.width(value) <= width ? value
                : font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }

    private static String chance(int basisPoints) {
        if (basisPoints < 0) {
            return "?";
        }
        if (basisPoints % 100 == 0) {
            return Integer.toString(basisPoints / 100);
        }
        return String.format(Locale.ROOT, "%.2f", basisPoints / 100.0D);
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 250 && mouseX <= leftPos + 358
                && mouseY >= topPos + 34 && mouseY <= topPos + 56) {
            graphics.renderTooltip(font, Component.literal(
                    "Carry a stack, then click: creates a new one-roll pool where that item is guaranteed."), mouseX, mouseY);
        } else if (mouseX >= leftPos + 336 && mouseX <= leftPos + 414
                && mouseY >= topPos + 80 && mouseY <= topPos + 112) {
            graphics.renderTooltip(font, Component.literal(
                    "Direct random-chance gate. 100% removes that gate; other conditions remain."), mouseX, mouseY);
        } else if (mouseX >= leftPos + 250 && mouseX <= leftPos + 414
                && mouseY >= topPos + 172 && mouseY <= topPos + 204) {
            graphics.renderTooltip(font, Component.literal(
                    "Rolls are independent selections from this pool. A range chooses a random roll count."), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < LootEditorMenu.ENTRY_SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            if (menu.entry(i) != null && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)
                    && menu.getCarried().isEmpty()) {
                selectEntry(i);
                break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
