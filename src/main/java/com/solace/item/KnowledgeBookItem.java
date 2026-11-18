package com.solace.item;

import com.solace.quests.locks.PonderLocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A knowledge book: {knowledge:"&lt;id&gt;", title:"&lt;name&gt;"}. Reading it opens that knowledge's ponders. */
public class KnowledgeBookItem extends Item {
    public KnowledgeBookItem(Properties properties) {
        super(properties);
    }

    public static ResourceLocation knowledge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : ResourceLocation.tryParse(tag.getString("knowledge"));
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("title", Tag.TAG_STRING) ? Component.literal(tag.getString("title"))
                : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.solace.knowledge_book.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            PonderLocks.learn(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
