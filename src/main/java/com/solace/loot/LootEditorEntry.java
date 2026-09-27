package com.solace.loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record LootEditorEntry(String path, ItemStack stack, int weight, int poolWeight,
                              boolean external, String externalItemId) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(path, 512);
        buffer.writeItem(stack);
        buffer.writeVarInt(weight);
        buffer.writeVarInt(poolWeight);
        buffer.writeBoolean(external);
        buffer.writeUtf(externalItemId, 256);
    }

    public static LootEditorEntry read(FriendlyByteBuf buffer) {
        return new LootEditorEntry(buffer.readUtf(512), buffer.readItem(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readUtf(256));
    }
}
