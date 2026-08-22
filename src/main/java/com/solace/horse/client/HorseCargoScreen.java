package com.solace.horse.client;

import com.solace.horse.HorseCargoData;
import com.solace.horse.HorseCargoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class HorseCargoScreen extends AbstractContainerScreen<HorseCargoMenu> {
    private static final ResourceLocation GENERIC_54 = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final ResourceLocation HORSE = new ResourceLocation("minecraft", "textures/gui/container/horse.png");
    private static final int HORSE_EMPTY_SLOT_V = 220;
    private static final int EMPTY_SACK_COLOR = 0xFF686868;
    private static final int GUI_TEXTURE_SIZE = 256;

    public HorseCargoScreen(HorseCargoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 232;
        imageHeight = 222;
        inventoryLabelX = 64;
        inventoryLabelY = 128;
        titleLabelX = 64;
        titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderEquipmentPanel(graphics, leftPos, topPos);
        graphics.blit(GENERIC_54, leftPos + 56, topPos, 0, 0, 176, 222);
        clearCargoGrid(graphics);

        if (menu.getSlot(0).isActive()) {
            graphics.blit(HORSE, leftPos + 7, topPos + 17, 18, HORSE_EMPTY_SLOT_V, 18, 18);
        }
        if (menu.getSlot(1).isActive()) {
            graphics.blit(HORSE, leftPos + 7, topPos + 35, 0, HORSE_EMPTY_SLOT_V, 18, 18);
        }

        for (int row = 0; row < HorseCargoData.MAX_SACKS; row++) {
            Slot sackSlot = menu.getSlot(2 + row);
            if (sackSlot.isActive()) {
                int sackY = topPos + 17 + row * 18;
                graphics.blit(GENERIC_54, leftPos + 29, sackY, 7, 17, 18, 18);
                if (!sackSlot.hasItem()) {
                    renderEmptySackIcon(graphics, leftPos + 29, sackY);
                }
            }

            int firstCargoSlot = 2 + HorseCargoData.MAX_SACKS + row * HorseCargoData.SLOTS_PER_SACK;
            if (menu.getSlot(firstCargoSlot).isActive()) {
                for (int column = 0; column < HorseCargoData.SLOTS_PER_SACK; column++) {
                    graphics.blit(GENERIC_54, leftPos + 63 + column * 18, topPos + 17 + row * 18,
                            7, 17, 18, 18);
                }
            }
        }
    }

    private void clearCargoGrid(GuiGraphics graphics) {
        blitStretched(graphics, leftPos + 63, topPos + 17,
                HorseCargoData.SLOTS_PER_SACK * 18, HorseCargoData.MAX_SACKS * 18,
                10, 10, 1, 1);
    }

    private static void renderEmptySackIcon(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 7, y + 3, x + 11, y + 4, EMPTY_SACK_COLOR);
        graphics.fill(x + 6, y + 4, x + 7, y + 5, EMPTY_SACK_COLOR);
        graphics.fill(x + 11, y + 4, x + 12, y + 5, EMPTY_SACK_COLOR);
        graphics.fill(x + 7, y + 5, x + 11, y + 6, EMPTY_SACK_COLOR);
        graphics.fill(x + 5, y + 6, x + 13, y + 7, EMPTY_SACK_COLOR);
        graphics.fill(x + 4, y + 7, x + 5, y + 9, EMPTY_SACK_COLOR);
        graphics.fill(x + 13, y + 7, x + 14, y + 9, EMPTY_SACK_COLOR);
        graphics.fill(x + 3, y + 9, x + 4, y + 13, EMPTY_SACK_COLOR);
        graphics.fill(x + 14, y + 9, x + 15, y + 13, EMPTY_SACK_COLOR);
        graphics.fill(x + 4, y + 13, x + 5, y + 14, EMPTY_SACK_COLOR);
        graphics.fill(x + 13, y + 13, x + 14, y + 14, EMPTY_SACK_COLOR);
        graphics.fill(x + 5, y + 14, x + 13, y + 15, EMPTY_SACK_COLOR);
    }

    private static void renderEquipmentPanel(GuiGraphics graphics, int x, int y) {
        int width = 56;
        int height = 132;
        int border = 7;

        blitStretched(graphics, x + border, y + border,
                width - border * 2, height - border * 2, 10, 10, 1, 1);
        blitStretched(graphics, x + border, y,
                width - border * 2, border, 8, 0, 1, border);
        blitStretched(graphics, x + border, y + height - border,
                width - border * 2, border, 8, 215, 1, border);
        blitStretched(graphics, x, y + border,
                border, height - border * 2, 0, 8, border, 1);
        blitStretched(graphics, x + width - border, y + border,
                border, height - border * 2, 169, 8, border, 1);

        graphics.blit(GENERIC_54, x, y, 0, 0, border, border);
        graphics.blit(GENERIC_54, x + width - border, y, 169, 0, border, border);
        graphics.blit(GENERIC_54, x, y + height - border, 0, 215, border, border);
        graphics.blit(GENERIC_54, x + width - border, y + height - border, 169, 215, border, border);
    }

    private static void blitStretched(GuiGraphics graphics, int x, int y, int width, int height,
                                      int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        graphics.blit(GENERIC_54, x, y, width, height, sourceX, sourceY,
                sourceWidth, sourceHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
