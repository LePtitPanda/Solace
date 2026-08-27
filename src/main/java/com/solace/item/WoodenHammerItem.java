package com.solace.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WoodenHammerItem extends AxeItem {
    public WoodenHammerItem(Properties properties) {
        super(Tiers.WOOD, 6.0F, -3.2F, properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return isHammerMineable(state) ? speed : 1.0F;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isCorrectToolForDrops(BlockState state) {
        if (!isHammerMineable(state)) {
            return false;
        }

        if (TierSortingRegistry.isTierSorted(getTier())) {
            return TierSortingRegistry.isCorrectTierForDrops(getTier(), state);
        }

        int tierLevel = getTier().getLevel();
        if (tierLevel < 3 && state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return false;
        }
        if (tierLevel < 2 && state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return false;
        }
        return tierLevel >= 1 || !state.is(BlockTags.NEEDS_STONE_TOOL);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return isHammerMineable(state)
                && TierSortingRegistry.isCorrectTierForDrops(getTier(), state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_AXE_ACTIONS.contains(toolAction)
                || ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(toolAction);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.solace.wooden_hammer.tooltip").withStyle(ChatFormatting.GRAY));
    }

    private static boolean isHammerMineable(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }
}
