package com.solace.item;

import com.solace.loot.LootEditorOpener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class LootTableEditorItem extends Item {
    public LootTableEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("The Loot Table Editor is a creative/operator tool."), true);
            return InteractionResult.FAIL;
        }
        return LootEditorOpener.openFromBlock(player, context.getClickedPos(), 0)
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
}
