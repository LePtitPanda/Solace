package com.solace.network;

import com.solace.Solace;
import com.solace.loot.LootEditorManager;
import com.solace.loot.LootEditorOpener;
import com.solace.menu.LootEditorMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class LootEditorNetwork {
    private static final String PROTOCOL = "1";
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
    }

    public static void changeTable(String target) {
        CHANNEL.sendToServer(new ChangeTable(target));
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
            LootEditorOpener.reopen(player, menu.snapshot().pos(), menu.snapshot().source(), 0);
            context.setPacketHandled(true);
        }
    }
}
