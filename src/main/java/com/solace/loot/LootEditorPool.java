package com.solace.loot;

import net.minecraft.network.FriendlyByteBuf;

public record LootEditorPool(int index, String name,
                             double rollsMin, double rollsMax, boolean rollsEditable,
                             double bonusRollsMin, double bonusRollsMax, boolean bonusRollsEditable,
                             int itemEntries, int otherEntries, int conditions) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(index);
        buffer.writeUtf(name, 256);
        buffer.writeDouble(rollsMin);
        buffer.writeDouble(rollsMax);
        buffer.writeBoolean(rollsEditable);
        buffer.writeDouble(bonusRollsMin);
        buffer.writeDouble(bonusRollsMax);
        buffer.writeBoolean(bonusRollsEditable);
        buffer.writeVarInt(itemEntries);
        buffer.writeVarInt(otherEntries);
        buffer.writeVarInt(conditions);
    }

    public static LootEditorPool read(FriendlyByteBuf buffer) {
        return new LootEditorPool(buffer.readVarInt(), buffer.readUtf(256),
                buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(),
                buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }
}
