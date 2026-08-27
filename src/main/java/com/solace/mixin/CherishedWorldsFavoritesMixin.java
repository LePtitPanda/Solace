package com.solace.mixin;

import com.illusivesoulworks.cherishedworlds.client.favorites.FavoriteWorlds;
import com.illusivesoulworks.cherishedworlds.integration.ViewerIntegration;
import com.mojang.datafixers.util.Pair;
import com.solace.client.gui.WorldCreationThemeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FavoriteWorlds.class, remap = false)
public abstract class CherishedWorldsFavoritesMixin {
    @Redirect(method = "draw(IILnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/screens/worldselection/SelectWorldScreen;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/illusivesoulworks/cherishedworlds/client/favorites/FavoriteWorlds;drawIcon(IILnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/screens/Screen;IZIDI)V"),
            remap = false)
    private void solace$renderFavoriteControl(FavoriteWorlds viewer, int mouseX, int mouseY,
                                              GuiGraphics graphics, Screen screen, int index,
                                              boolean favorite, int listTop, double scrollAmount,
                                              int listBottom) {
        int topOffset = 15;
        int rowHeight = 36;
        Pair<Integer, Integer> override = ViewerIntegration.getOverride(rowHeight);
        if (override != null) {
            topOffset = override.getFirst();
            rowHeight = override.getSecond();
        }

        int y = (int) (listTop + topOffset + rowHeight * index - scrollAmount);
        int x = screen.width / 2 - viewer.getHorizontalOffset();
        if (y < listBottom - 8 && y > listTop) {
            boolean hovered = mouseX >= x && mouseX <= x + 9 && mouseY >= y && mouseY <= y + 9;
            WorldCreationThemeRenderer.renderFavoriteControl(graphics, x, y, favorite, hovered);
            if (hovered) {
                Component tooltip = Component.translatable(favorite
                        ? "selectWorld.cherishedworlds.unfavorite"
                        : "selectWorld.cherishedworlds.favorite");
                graphics.renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
            }
        }
    }
}
