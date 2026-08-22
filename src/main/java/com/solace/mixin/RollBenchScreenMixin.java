package com.solace.mixin;

import com.solace.access.BookChoiceLevelsAccess;
import com.solace.world.EnchantingReworkSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.backupcup.mcde.util.Choice;
import net.backupcup.mcde.util.EnchantmentSlots;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.backupcup.mcde.screen.RollBenchScreen", remap = false)
public class RollBenchScreenMixin {
    @Redirect(
            method = "renderTooltip",
            at = @At(value = "INVOKE", target = "Lnet/backupcup/mcde/util/Choice;isChosen()Z"),
            remap = false
    )
    private boolean solace$showBookshelfChoiceLevel(Choice choice) {
        int sourceLevel = ((BookChoiceLevelsAccess) choice.getEnchantmentSlot())
                .solace$getChoiceLevel(choice.getChoicePosition());
        if (sourceLevel > 0) {
            int displayedLevel = choice.isChosen()
                    ? choice.getEnchantmentSlot().getLevel()
                    : sourceLevel;
            return displayedLevel > 1;
        }
        return choice.isChosen();
    }

    @ModifyVariable(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/backupcup/mcde/screen/handler/RollBenchScreenHandler;isSlotLocked(Lnet/backupcup/mcde/util/SlotPosition;)Ljava/util/Optional;",
                    shift = At.Shift.BEFORE
            ),
            ordinal = 0,
            remap = false
    )
    private List<Component> solace$showBookshelfXpCost(List<Component> lines, GuiGraphics graphics,
                                                        Choice choice, EnchantmentSlots slots,
                                                        int mouseX, int mouseY) {
        int xpCost = EnchantingReworkSupport.bookshelfChoiceXpCost(choice);
        Minecraft minecraft = Minecraft.getInstance();
        boolean slotLocked = minecraft.player == null
                || EnchantingReworkSupport.isBookshelfChoiceSlotLocked(minecraft.player.containerMenu, choice);

        List<Component> decorated = lines;
        Optional<Component> upgradeLabel = slotLocked
                ? Optional.empty()
                : EnchantingReworkSupport.bookshelfUpgradeLabel(choice);
        if (upgradeLabel.isPresent()) {
            decorated = new ArrayList<>(lines);
            if (decorated.isEmpty()) {
                decorated.add(upgradeLabel.get());
            } else {
                decorated.set(0, upgradeLabel.get());
            }
        }

        if (xpCost > 0 && !slotLocked && minecraft.player != null && !minecraft.player.isCreative()) {
            if (decorated == lines) {
                decorated = new ArrayList<>(lines);
            }
            decorated.add(Component.translatable("message.mcde.levels_required", xpCost)
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            if (minecraft.player.experienceLevel < xpCost) {
                decorated.add(Component.translatable("message.mcde.not_enough_levels")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            }
        }
        return decorated;
    }
}
