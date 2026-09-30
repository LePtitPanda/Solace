package com.solace.network;

import com.solace.Solace;
import com.solace.loot.LootEditorEntry;
import com.solace.loot.LootEditorManager;
import com.solace.loot.LootEditorOpener;
import com.solace.loot.LootEditorPool;
import com.solace.menu.LootEditorMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class LootEditorNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Solace.MOD_ID, "loot_editor"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private LootEditorNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(ChangeTable.class, 0)
                .encoder(ChangeTable::encode)
                .decoder(ChangeTable::decode)
                .consumerMainThread(ChangeTable::handle)
                .add();
        CHANNEL.messageBuilder(EditPool.class, 1)
                .encoder(EditPool::encode)
                .decoder(EditPool::decode)
                .consumerMainThread(EditPool::handle)
                .add();
        CHANNEL.messageBuilder(EditEntry.class, 2)
                .encoder(EditEntry::encode)
                .decoder(EditEntry::decode)
                .consumerMainThread(EditEntry::handle)
                .add();
    }

    public static void changeTable(String target) {
        CHANNEL.sendToServer(new ChangeTable(target));
    }

    public static void editPool(double rollsMin, double rollsMax, double bonusMin, double bonusMax) {
        CHANNEL.sendToServer(new EditPool(rollsMin, rollsMax, bonusMin, bonusMax));
    }

    public static void editEntry(int slot, int weight, int chanceBasisPoints, int countMin, int countMax) {
        CHANNEL.sendToServer(new EditEntry(slot, weight, chanceBasisPoints, countMin, countMax));
    }

    private record ChangeTable(String target) {
        private static void encode(ChangeTable packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.target, 256);
        }

        private static ChangeTable decode(FriendlyByteBuf buffer) {
            return new ChangeTable(buffer.readUtf(256));
        }

        private static void handle(ChangeTable packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
            net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof LootEditorMenu menu)
                    || (!player.isCreative() && !player.hasPermissions(2))) {
                context.setPacketHandled(true);
                return;
            }
            ResourceLocation target = ResourceLocation.tryParse(packet.target.trim());
            if (target == null || !LootEditorManager.setReplacement(player.server, menu.snapshot().source(), target)) {
                player.displayClientMessage(Component.literal("Unknown loot table: " + packet.target), true);
                context.setPacketHandled(true);
                return;
            }
            LootEditorOpener.reopen(player, menu.snapshot().pos(), menu.snapshot().source(), 0, 0);
            context.setPacketHandled(true);
        }
    }

    private record EditPool(double rollsMin, double rollsMax, double bonusMin, double bonusMax) {
        private static void encode(EditPool packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.rollsMin);
            buffer.writeDouble(packet.rollsMax);
            buffer.writeDouble(packet.bonusMin);
            buffer.writeDouble(packet.bonusMax);
        }

        private static EditPool decode(FriendlyByteBuf buffer) {
            return new EditPool(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        private static void handle(EditPool packet,
                                   Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
            net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof LootEditorMenu menu)
                    || (!player.isCreative() && !player.hasPermissions(2))) {
                context.setPacketHandled(true);
                return;
            }
            int poolIndex = menu.snapshot().selectedPool();
            LootEditorPool pool = poolIndex >= 0 && poolIndex < menu.snapshot().pools().size()
                    ? menu.snapshot().pools().get(poolIndex) : null;
            if (pool == null || !finite(packet.rollsMin, packet.rollsMax, packet.bonusMin, packet.bonusMax)
                    || !LootEditorManager.setPoolSettings(player.server, menu.snapshot().source(), poolIndex,
                    packet.rollsMin, packet.rollsMax, packet.bonusMin, packet.bonusMax)) {
                player.displayClientMessage(Component.literal("Could not edit this pool's roll provider."), true);
                context.setPacketHandled(true);
                return;
            }
            LootEditorOpener.reopen(player, menu.snapshot().pos(), menu.snapshot().source(), poolIndex,
                    menu.snapshot().page());
            context.setPacketHandled(true);
        }
    }

    private record EditEntry(int slot, int weight, int chanceBasisPoints, int countMin, int countMax) {
        private static void encode(EditEntry packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.slot);
            buffer.writeVarInt(packet.weight);
            buffer.writeVarInt(packet.chanceBasisPoints);
            buffer.writeVarInt(packet.countMin);
            buffer.writeVarInt(packet.countMax);
        }

        private static EditEntry decode(FriendlyByteBuf buffer) {
            return new EditEntry(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }

        private static void handle(EditEntry packet,
                                   Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
            net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof LootEditorMenu menu)
                    || (!player.isCreative() && !player.hasPermissions(2))) {
                context.setPacketHandled(true);
                return;
            }
            LootEditorEntry entry = menu.entry(packet.slot);
            boolean changed = false;
            if (entry != null) {
                if (entry.external()) {
                    ResourceLocation original = ResourceLocation.tryParse(entry.externalItemId());
                    if (original != null) {
                        changed = LootEditorManager.setExtraSettings(player.server, menu.snapshot().source(), original,
                                entry.stack(), packet.chanceBasisPoints, packet.countMin);
                    }
                } else {
                    changed = LootEditorManager.setEntrySettings(player.server, menu.snapshot().source(), entry.path(),
                            packet.weight, packet.chanceBasisPoints, packet.countMin, packet.countMax,
                            entry.countEditable());
                }
            }
            if (!changed) {
                player.displayClientMessage(Component.literal("Could not edit this loot entry."), true);
                context.setPacketHandled(true);
                return;
            }
            LootEditorOpener.reopen(player, menu.snapshot().pos(), menu.snapshot().source(),
                    menu.snapshot().selectedPool(), menu.snapshot().page());
            context.setPacketHandled(true);
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
