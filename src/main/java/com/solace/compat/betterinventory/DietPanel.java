package com.solace.compat.betterinventory;

import com.illusivesoulworks.diet.api.type.IDietGroup;
import com.illusivesoulworks.diet.api.type.IDietTracker;
import com.illusivesoulworks.diet.common.data.suite.DietSuites;
import com.illusivesoulworks.diet.platform.Services;
import dev.fallingcloud.betterinventory.client.ClientTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/** The Diet panel: one row per food group (icon, name, bar in the group's colour). Only loaded with Diet. */
final class DietPanel {
    private DietPanel() {
    }

    static Component render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        Player player = Minecraft.getInstance().player;
        IDietTracker tracker = player == null ? null : Services.CAPABILITY.get(player).orElse(null);
        if (tracker == null) {
            return null;
        }
        List<IDietGroup> groups = DietSuites.getSuite(player.level(), tracker.getSuite())
                .map(suite -> suite.getGroups().stream().sorted(Comparator.comparingInt(IDietGroup::getOrder)).toList())
                .orElse(List.of());
        if (groups.isEmpty()) {
            return null;
        }
        int pitch = Math.min(23, 116 / groups.size());
        Component tooltip = null;
        for (int i = 0; i < groups.size(); i++) {
            IDietGroup group = groups.get(i);
            int rowY = y + 8 + i * pitch;
            float value = tracker.getValue(group.getName());
            Component name = Component.translatable("groups.diet." + group.getName() + ".name");
            graphics.renderItem(new ItemStack(group.getIcon()), x + 5, rowY + 1);
            graphics.pose().pushPose();
            graphics.pose().translate(x + 24, rowY + 1, 0);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.drawString(font, name, 0, 0, ClientTheme.panelText(), false);
            graphics.pose().popPose();
            int color = group.getColor().red() << 16 | group.getColor().green() << 8 | group.getColor().blue();
            BetterInventoryExtras.bar(graphics, x + 24, rowY + 9, 48, 5, value, color);
            if (BetterInventoryExtras.over(x + 3, rowY, 72, pitch, mouseX, mouseY)) {
                tooltip = Component.empty().append(name).append(": " + Math.round(value * 100) + "%");
            }
        }
        if (!tracker.isActive()) {
            graphics.drawCenteredString(font, Component.translatable("gui.diet.title"), x + 39, y + 120, ClientTheme.panelTextDim());
        }
        return tooltip;
    }
}
