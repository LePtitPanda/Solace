package com.solace.quests.state;

import com.solace.quests.condition.Ctx;
import com.solace.quests.content.QuestContent;
import com.solace.quests.content.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** {@link Ctx} backed by a live player and the world's quest store. */
public final class ServerCtx extends Ctx {
    public final ServerPlayer player;
    private final QuestSavedData store;
    private final PlayerQuestData data;

    ServerCtx(ServerPlayer player) {
        this.player = player;
        this.store = QuestSavedData.get(player.server);
        // v1: every player is their own progress owner. Party progress changes only this line.
        this.data = store.owner(player.getUUID());
    }

    @Override
    public QuestContent content() {
        return QuestContent.current;
    }

    @Override
    public PlayerQuestData data() {
        return data;
    }

    @Override
    public long gameTime() {
        return player.server.overworld().getGameTime();
    }

    @Override
    public String dimension() {
        return player.level().dimension().location().toString();
    }

    @Override
    public long dayTime() {
        return player.level().getDayTime() % 24000L;
    }

    @Override
    public int countItem(Target item, boolean heldOnly) {
        if (heldOnly) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            return (item.matches(main) ? main.getCount() : 0) + (item.matches(off) ? off.getCount() : 0);
        }
        // maxCount 0 = count without removing.
        return player.getInventory().clearOrCountMatchingItems(item::matches, 0, player.inventoryMenu.getCraftSlots());
    }

    @Override
    public boolean predicate(ResourceLocation id) {
        LootItemCondition condition = player.server.getLootData().getElement(LootDataType.PREDICATE, id);
        if (condition == null) {
            return false;
        }
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.COMMAND);
        return condition.test(new LootContext.Builder(params).create((ResourceLocation) null));
    }

    @Override
    public void changed() {
        data.index = null;
        store.setDirty();
    }
}
