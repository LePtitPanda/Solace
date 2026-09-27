package com.solace.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record LootEditorSnapshot(BlockPos pos, ResourceLocation source, ResourceLocation effective,
                                 int page, int pageCount, int totalEntries,
                                 int directEntries, int externalEntries,
                                 List<LootEditorEntry> entries, List<ResourceLocation> structures,
                                 boolean structureIndexReady, boolean overridden, boolean suppressModifiers) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeResourceLocation(source);
        buffer.writeResourceLocation(effective);
        buffer.writeVarInt(page);
        buffer.writeVarInt(pageCount);
        buffer.writeVarInt(totalEntries);
        buffer.writeVarInt(directEntries);
        buffer.writeVarInt(externalEntries);
        buffer.writeVarInt(entries.size());
        entries.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(structures.size());
        structures.forEach(buffer::writeResourceLocation);
        buffer.writeBoolean(structureIndexReady);
        buffer.writeBoolean(overridden);
        buffer.writeBoolean(suppressModifiers);
    }

    public static LootEditorSnapshot read(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        ResourceLocation source = buffer.readResourceLocation();
        ResourceLocation effective = buffer.readResourceLocation();
        int page = buffer.readVarInt();
        int pageCount = buffer.readVarInt();
        int totalEntries = buffer.readVarInt();
        int directEntries = buffer.readVarInt();
        int externalEntries = buffer.readVarInt();
        int entryCount = buffer.readVarInt();
        List<LootEditorEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(LootEditorEntry.read(buffer));
        }
        int structureCount = buffer.readVarInt();
        List<ResourceLocation> structures = new ArrayList<>(structureCount);
        for (int i = 0; i < structureCount; i++) {
            structures.add(buffer.readResourceLocation());
        }
        return new LootEditorSnapshot(pos, source, effective, page, pageCount, totalEntries,
                directEntries, externalEntries,
                List.copyOf(entries), List.copyOf(structures), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean());
    }
}
