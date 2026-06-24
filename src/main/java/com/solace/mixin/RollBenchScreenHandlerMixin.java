package com.solace.mixin;

import com.solace.config.EnchantingReworkConfig;
import com.solace.world.EnchantingReworkSupport;
import java.util.Optional;
import net.backupcup.mcde.util.EnchantmentSlots;
import net.backupcup.mcde.util.SlotPosition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.backupcup.mcde.screen.handler.RollBenchScreenHandler", remap = false)
public class RollBenchScreenHandlerMixin {
    @Unique
    private int solace$rerollMaterialBefore = -1;
    @Unique
    private int solace$rerollXpCost = 0;
    @Unique
    private ItemStack solace$rerollMaterialStack = ItemStack.EMPTY;
    @Unique
    private ItemStack solace$rerollItemBefore = ItemStack.EMPTY;

    @Inject(method = {"stillValid", "m_6875_", "canUse"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$stillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(EnchantingReworkSupport.stillValidAtAtoningTable(this, player));
    }

    @Inject(method = {"method_7604", "m_6366_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$beforeRerollClick(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        solace$clearRerollCost();
        Optional<ContainerLevelAccess> context = EnchantingReworkSupport.context(this);
        if (context.isEmpty()) {
            return;
        }

        int requiredPower = EnchantingReworkConfig.rerollPowerRequiredForButton(buttonId);
        if (!EnchantingReworkSupport.hasEnchantingPower(context.get(), requiredPower)) {
            cir.setReturnValue(false);
            return;
        }

        Optional<EnchantingReworkSupport.RerollClick> click = EnchantingReworkSupport.inspectRerollClick(this, buttonId);
        Optional<Container> inventory = EnchantingReworkSupport.inventory(this);
        if (click.isEmpty() || inventory.isEmpty()) {
            return;
        }

        solace$rerollItemBefore = inventory.get().getItem(0).copy();
        if (player.isCreative()) {
            return;
        }

        int discount = EnchantingReworkSupport.xpCostReduction(context.get(), click.get().baseCost());
        int xpCost = Math.max(1, click.get().baseCost() - discount);
        if (player.experienceLevel < xpCost) {
            cir.setReturnValue(false);
            return;
        }

        ItemStack material = inventory.get().getItem(1);
        solace$rerollMaterialBefore = material.getCount();
        solace$rerollMaterialStack = material.copy();
        solace$rerollXpCost = xpCost;
    }

    @Inject(method = {"method_7604", "m_6366_"}, at = @At("RETURN"), remap = false)
    private void solace$afterRerollClick(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        Optional<Container> inventory = EnchantingReworkSupport.inventory(this);
        if (inventory.isEmpty()) {
            solace$clearRerollCost();
            return;
        }

        boolean rerolled = !ItemStack.matches(solace$rerollItemBefore, inventory.get().getItem(0));
        if (rerolled) {
            EnchantingReworkSupport.normalizeHandlerStack(this);
            EnchantingReworkSupport.synchronizeHandlerInventory(this);
        }

        if (player.isCreative() || solace$rerollMaterialBefore < 0) {
            solace$clearRerollCost();
            return;
        }

        ItemStack current = inventory.get().getItem(1);
        int currentCount = current.getCount();
        int consumed = solace$rerollMaterialBefore - currentCount;
        if (consumed > 0) {
            ItemStack restored = current.isEmpty() ? solace$rerollMaterialStack.copy() : current;
            restored.grow(consumed);
            inventory.get().setItem(1, restored);
            inventory.get().setChanged();
        }
        if (rerolled) {
            player.giveExperienceLevels(-solace$rerollXpCost);
        }
        solace$clearRerollCost();
    }

    @Inject(method = "canReroll", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$allowXpPaidReroll(Player player, ResourceLocation enchantmentId, EnchantmentSlots slots, CallbackInfoReturnable<Boolean> cir) {
        if (player.isCreative()) {
            return;
        }

        Optional<ContainerLevelAccess> context = EnchantingReworkSupport.context(this);
        if (context.isEmpty()) {
            return;
        }

        int baseCost = EnchantingReworkSupport.rerollCost(slots, enchantmentId);
        int discount = EnchantingReworkSupport.xpCostReduction(context.get(), baseCost);
        int xpCost = Math.max(1, baseCost - discount);
        if (player.experienceLevel >= xpCost) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "generateEnchantment", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$gateGeneratedReroll(Player player, SlotPosition clickedSlot, CallbackInfoReturnable<Optional<?>> cir) {
        Optional<ContainerLevelAccess> context = EnchantingReworkSupport.context(this);
        if (context.isEmpty()) {
            return;
        }

        int requiredPower = EnchantingReworkConfig.rerollPowerRequiredForMainSlot(clickedSlot.ordinal());
        if (!EnchantingReworkSupport.hasEnchantingPower(context.get(), requiredPower)) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "generateEnchantment", at = @At("RETURN"), cancellable = true, remap = false)
    private void solace$fallbackGeneratedReroll(Player player, SlotPosition clickedSlot, CallbackInfoReturnable<Optional<?>> cir) {
        Optional<?> generated = cir.getReturnValue();
        if (generated != null && generated.isPresent()) {
            return;
        }

        Optional<ContainerLevelAccess> context = EnchantingReworkSupport.context(this);
        if (context.isEmpty()) {
            return;
        }

        int requiredPower = EnchantingReworkConfig.rerollPowerRequiredForMainSlot(clickedSlot.ordinal());
        if (EnchantingReworkSupport.hasEnchantingPower(context.get(), requiredPower)) {
            cir.setReturnValue(EnchantingReworkSupport.fallbackRerollChoice(this, clickedSlot));
        }
    }

    @Unique
    private void solace$clearRerollCost() {
        solace$rerollMaterialBefore = -1;
        solace$rerollXpCost = 0;
        solace$rerollMaterialStack = ItemStack.EMPTY;
        solace$rerollItemBefore = ItemStack.EMPTY;
    }
}
