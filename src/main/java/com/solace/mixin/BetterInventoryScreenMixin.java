package com.solace.mixin;

import com.solace.compat.betterinventory.BetterInventoryExtras;
import dev.fallingcloud.betterinventory.BetterInventoryConfig;
import dev.fallingcloud.betterinventory.client.UiSounds;
import dev.fallingcloud.betterinventory.client.screen.BetterInventoryScreen;
import dev.fallingcloud.betterinventory.menu.BetterInventoryMenu;
import dev.fallingcloud.betterinventory.net.BetterInventoryPayloads;
import dev.fallingcloud.betterinventory.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diet tab and panel, tiredness strip: see {@link BetterInventoryExtras}. */
@Mixin(value = BetterInventoryScreen.class, remap = false)
public abstract class BetterInventoryScreenMixin extends AbstractContainerScreen<BetterInventoryMenu> {
    @Shadow
    private int[] sideTabs;

    @Unique
    private Component solace$tooltip;

    @Shadow
    private void sheet(GuiGraphics graphics, int x, int y, int u, int v, int w, int h) {
        throw new AssertionError();
    }

    @Shadow
    private void charPanel(GuiGraphics graphics, int x, int y) {
        throw new AssertionError();
    }

    private BetterInventoryScreenMixin(BetterInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Redirect(method = {"renderSideTabs", "sideTabAt", "sideTabCenter"}, at = @At(value = "INVOKE",
            target = "Ldev/fallingcloud/betterinventory/BetterInventoryLayout;sideTabY(I)I"))
    private int solace$tabY(int index) {
        return BetterInventoryExtras.tabY(sideTabs, index);
    }

    /** Better Inventory's own panel opened (or JEI opened crafting): the Diet panel steps aside. */
    @Unique
    private boolean solace$dietOpen() {
        if (BetterInventoryExtras.dietOpen && menu.loadout.sidePanel != 0) {
            BetterInventoryExtras.dietOpen = false;
        }
        return BetterInventoryExtras.dietOpen;
    }

    @Inject(method = "renderSideTabs", at = @At("TAIL"))
    private void solace$dietTab(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!BetterInventoryExtras.diet()) {
            return;
        }
        int x = leftPos + 282, y = topPos + BetterInventoryExtras.dietTabY(sideTabs);
        boolean open = solace$dietOpen();
        sheet(graphics, x, y, 28, open ? 25 : 0, 24, 24); // selected tab: one row down on the sheet
        BetterInventoryExtras.dietIcon(graphics, x, y, open, BetterInventoryExtras.over(x, y, 24, 24, mouseX, mouseY));
    }

    @Inject(method = "renderPanel", at = @At("TAIL"))
    private void solace$panels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        solace$tooltip = null;
        if (BetterInventoryExtras.diet() && solace$dietOpen()) {
            charPanel(graphics, leftPos, topPos + 37);
            solace$tooltip = BetterInventoryExtras.dietPanel(graphics, leftPos, topPos + 37, mouseX, mouseY);
        }
        if (BetterInventoryExtras.tiredness()) {
            // Just above the left panel; above the effect icons when those are shown.
            boolean effects = BetterInventoryConfig.SHOW_EFFECTS.get() && minecraft != null && minecraft.player != null
                    && !minecraft.player.getActiveEffects().isEmpty();
            Component tip = BetterInventoryExtras.tirednessStrip(graphics, leftPos, topPos + (effects ? -6 : 24), mouseX, mouseY);
            if (tip != null) {
                solace$tooltip = tip;
            }
        }
    }

    @Inject(method = "m_88315_", at = @At("TAIL")) // render; Better Inventory ships with runtime (SRG) names
    private void solace$tooltips(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (menu.getCarried().isEmpty()) {
            if (BetterInventoryExtras.diet() && BetterInventoryExtras.over(leftPos + 282,
                    topPos + BetterInventoryExtras.dietTabY(sideTabs), 24, 24, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.diet.title"), mouseX, mouseY);
            } else if (solace$tooltip != null) {
                graphics.renderTooltip(font, solace$tooltip, mouseX, mouseY);
            }
        }
    }

    @Inject(method = "m_6375_", at = @At("HEAD"), cancellable = true) // mouseClicked
    private void solace$dietClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !BetterInventoryExtras.diet() || !BetterInventoryExtras.over(leftPos + 282,
                topPos + BetterInventoryExtras.dietTabY(sideTabs), 24, 24, mouseX, mouseY)) {
            return;
        }
        BetterInventoryExtras.dietOpen = !solace$dietOpen();
        if (BetterInventoryExtras.dietOpen && menu.loadout.sidePanel != 0) {
            menu.loadout.sidePanel = 0;
            Net.sendToServer(new BetterInventoryPayloads.SetSidePanel(0));
        }
        UiSounds.click();
        cir.setReturnValue(true);
    }
}
