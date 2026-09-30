package com.solace.loot;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import noobanidus.mods.lootr.api.blockentity.ILootBlockEntity;
import noobanidus.mods.lootr.data.DataStorage;

/** Optional Lootr integration, loaded only when Lootr is present. */
final class LootEditorLootrCompat {
    private LootEditorLootrCompat() {
    }

    static void reset(BlockEntity blockEntity, ServerPlayer player) {
        if (!(blockEntity instanceof ILootBlockEntity lootr)) {
            return;
        }
        if (lootr.getTileId() != null) {
            DataStorage.refreshInventory(player.level(), lootr.getPosition(), lootr.getTileId(), player);
        }
        // Lootr tracks the opened texture per player in this set. Only forget the
        // player whose personal inventory was refreshed; clearing everybody and
        // forcing the separate renderer flag leaves later block updates with an
        // invalid visual state even though the loot inventory was not reset.
        lootr.getOpeners().remove(player.getUUID());
        lootr.updatePacketViaState();
    }
}
