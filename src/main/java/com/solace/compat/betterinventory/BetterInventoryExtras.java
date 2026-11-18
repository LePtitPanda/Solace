package com.solace.compat.betterinventory;

import dev.fallingcloud.betterinventory.BetterInventoryLayout;
import dev.fallingcloud.betterinventory.client.ClientTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Better Inventory additions (BetterInventoryScreenMixin): a Diet side tab whose panel shows the food
 * groups, and a tiredness strip (Sleep Rework) above the left panel. The Diet tab sits where the player
 * tab was; the player tab moves one row up. The Diet panel is client-side: opening it closes Better
 * Inventory's own panel (its server only knows panels 0-6).
 */
public final class BetterInventoryExtras {
    public static final ResourceLocation DIET_ICON = new ResourceLocation("solace", "textures/gui/diet_tab.png");
    public static final ResourceLocation MOON_ICON = new ResourceLocation("solace", "textures/gui/tiredness.png");
    private static final int PLAYER_TAB = 1;
    private static final int HOVER_WASH = 1358954495;
    private static final int WELL_DARK = -12566464, WELL_VANILLA = -7631989;
    /** The Diet panel is open (kept between inventory openings, like Better Inventory's own panel). */
    public static boolean dietOpen;

    private BetterInventoryExtras() {
    }

    public static boolean diet() {
        return ModList.get().isLoaded("diet");
    }

    public static boolean tiredness() {
        return ModList.get().isLoaded("sleeprework");
    }

    /** Row of a Better Inventory side tab: with the Diet tab, the player tab (first) moves one row up. */
    public static int tabY(int[] sideTabs, int index) {
        boolean moved = diet() && index == 0 && sideTabs.length > 0 && sideTabs[0] == PLAYER_TAB;
        return BetterInventoryLayout.sideTabY(moved ? -1 : index);
    }

    /** The Diet tab's row: the player tab's old one, or above the first tab when there is no player tab. */
    public static int dietTabY(int[] sideTabs) {
        return BetterInventoryLayout.sideTabY(sideTabs.length > 0 && sideTabs[0] == PLAYER_TAB ? 0 : -1);
    }

    public static boolean over(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Diet tab face: the tab sprite is drawn by the caller (it is Better Inventory's sheet). */
    public static void dietIcon(GuiGraphics graphics, int x, int y, boolean open, boolean hovered) {
        if (!open) {
            graphics.setColor(0.62F, 0.62F, 0.62F, 1.0F);
        }
        graphics.blit(DIET_ICON, x + 4, y + 4, 0, 0, 16, 16, 16, 16);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (!open && hovered) {
            graphics.fill(x + 3, y + 3, x + 21, y + 21, HOVER_WASH);
        }
    }

    static int well() {
        return ClientTheme.vanillaStyle() ? WELL_VANILLA : WELL_DARK;
    }

    /** One bar in the panel style: a 1px well around the fill. */
    static void bar(GuiGraphics graphics, int x, int y, int w, int h, float fill, int color) {
        graphics.fill(x, y, x + w, y + h, well());
        int filled = Math.round((w - 2) * Math.max(0, Math.min(1, fill)));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, 0xFF000000 | color);
        }
    }

    /** Diet panel content inside the left panel frame (78 x 130 at x, y); returns a tooltip for the pointer. */
    public static Component dietPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        return DietPanel.render(graphics, Minecraft.getInstance().font, x, y, mouseX, mouseY);
    }

    /** Tiredness strip (78 wide, 12 high) at x, y; returns a tooltip for the pointer. */
    public static Component tirednessStrip(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        return TirednessStrip.render(graphics, Minecraft.getInstance().font, x, y, mouseX, mouseY);
    }
}
