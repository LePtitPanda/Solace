package com.solace.mixin;

import com.solace.config.EnchantingReworkConfig;
import com.solace.world.EnchantingReworkSupport;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.backupcup.mcde.screen.handler.RunicTableScreenHandler", remap = false)
public class RunicTableScreenHandlerMixin {
    @Unique
    private int solace$xpBeforeClick = -1;
    @Unique
    private int solace$baseCost = 0;
    @Unique
    private int solace$xpGrant = 0;
    @Unique
    private int solace$remainingRefund = 0;
    @Unique
    private ItemStack solace$itemBeforeClick = ItemStack.EMPTY;

    @Inject(method = {"stillValid", "m_6875_", "canUse"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$stillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(EnchantingReworkSupport.stillValidAtEnchantingTable(this, player));
    }

    @Inject(method = {"method_7604", "m_6366_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$beforeEnchantClick(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        solace$clearXpAdjustment();
        Optional<ContainerLevelAccess> context = EnchantingReworkSupport.context(this);
        Optional<EnchantingReworkSupport.RunicClick> click = EnchantingReworkSupport.inspectRunicClick(this, buttonId);
        if (context.isEmpty() || click.isEmpty()) {
            return;
        }

        int requiredPower = EnchantingReworkConfig.enchantingPowerRequiredForLevel(click.get().level());
        if (!EnchantingReworkSupport.hasEnchantingPower(context.get(), requiredPower)) {
            cir.setReturnValue(false);
            return;
        }

        EnchantingReworkSupport.inventory(this)
                .map(inventory -> inventory.getItem(0).copy())
                .ifPresent(stack -> solace$itemBeforeClick = stack);

        if (player.isCreative()) {
            return;
        }

        int discount = EnchantingReworkSupport.xpCostReduction(context.get(), click.get().baseCost());
        int discountedCost = Math.max(1, click.get().baseCost() - discount);
        if (player.experienceLevel < discountedCost) {
            cir.setReturnValue(false);
            return;
        }

        solace$xpBeforeClick = player.experienceLevel;
        solace$baseCost = click.get().baseCost();
        if (player.experienceLevel < click.get().baseCost()) {
            solace$xpGrant = click.get().baseCost() - player.experienceLevel;
            player.giveExperienceLevels(solace$xpGrant);
        }
        solace$remainingRefund = Math.max(0, discount - solace$xpGrant);
    }

    @Inject(method = {"method_7604", "m_6366_"}, at = @At("RETURN"), remap = false)
    private void solace$afterEnchantClick(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        boolean enchanted = EnchantingReworkSupport.inventory(this)
                .map(inventory -> inventory.getItem(0))
                .map(stack -> !ItemStack.matches(solace$itemBeforeClick, stack))
                .orElse(false);
        if (enchanted) {
            EnchantingReworkSupport.normalizeHandlerStack(this);
            EnchantingReworkSupport.synchronizeHandlerInventory(this);
        }

        if (player.isCreative() || solace$xpBeforeClick < 0) {
            solace$clearXpAdjustment();
            return;
        }

        int successLevel = solace$xpBeforeClick + solace$xpGrant - solace$baseCost;
        if (player.experienceLevel <= successLevel) {
            if (solace$remainingRefund > 0) {
                player.giveExperienceLevels(solace$remainingRefund);
            }
        } else if (solace$xpGrant > 0) {
            player.giveExperienceLevels(-solace$xpGrant);
        }
        solace$clearXpAdjustment();
    }

    @Unique
    private void solace$clearXpAdjustment() {
        solace$xpBeforeClick = -1;
        solace$baseCost = 0;
        solace$xpGrant = 0;
        solace$remainingRefund = 0;
        solace$itemBeforeClick = ItemStack.EMPTY;
    }
}
