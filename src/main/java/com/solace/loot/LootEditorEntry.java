package com.solace.loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record LootEditorEntry(String path, ItemStack stack, int poolIndex,
                              int weight, int poolWeight, int chanceBasisPoints,
                              int otherConditions, int countMin, int countMax,
                              boolean countEditable, int otherFunctions, boolean direct,
                              boolean external, String externalItemId) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(path, 512);
        buffer.writeItem(stack);
        buffer.writeVarInt(poolIndex);
        buffer.writeVarInt(weight);
        buffer.writeVarInt(poolWeight);
        buffer.writeVarInt(chanceBasisPoints);
        buffer.writeVarInt(otherConditions);
        buffer.writeVarInt(countMin);
        buffer.writeVarInt(countMax);
        buffer.writeBoolean(countEditable);
        buffer.writeVarInt(otherFunctions);
        buffer.writeBoolean(direct);
        buffer.writeBoolean(external);
        buffer.writeUtf(externalItemId, 256);
    }

    public static LootEditorEntry read(FriendlyByteBuf buffer) {
        return new LootEditorEntry(buffer.readUtf(512), buffer.readItem(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(256));
    }
}
