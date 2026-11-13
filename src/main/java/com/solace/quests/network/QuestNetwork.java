package com.solace.quests.network;

import com.solace.Solace;
import com.solace.client.quests.ClientQuests;
import com.solace.quests.content.Quest;
import com.solace.quests.content.QuestContent;
import com.solace.quests.dialogue.DialogueManager;
import com.solace.quests.network.Views.ChapterView;
import com.solace.quests.network.Views.FactionView;
import com.solace.quests.network.Views.MarkerView;
import com.solace.quests.network.Views.QuestView;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.QuestLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Channel solace:quests. Server to client: views of state. Client to server: one {@link Intent}
 * packet that only names what the player wants; the server decides whether it happens.
 */
public final class QuestNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Solace.MOD_ID, "quests"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    public static final int CLAIM = 0, TRACK = 1, CHECK = 2, ABANDON = 3, DIALOGUE_CHOICE = 4, DIALOGUE_ADVANCE = 5,
            DIALOGUE_CLOSE = 6;

    /** Token bucket per player: 20 intents burst, 10 per second refill. Excess is dropped. */
    private static final Map<UUID, double[]> BUCKETS = new HashMap<>();

    private QuestNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(Intent.class, 0).encoder(Intent::encode).decoder(Intent::decode)
                .consumerMainThread(Intent::handle).add();
        CHANNEL.messageBuilder(DialogueNode.class, 1).encoder(DialogueNode::encode).decoder(DialogueNode::decode)
                .consumerMainThread((packet, context) -> client(context, () -> () -> ClientQuests.dialogue(packet))).add();
        CHANNEL.messageBuilder(DialogueClose.class, 2).encoder((p, b) -> b.writeVarInt(p.session))
                .decoder(b -> new DialogueClose(b.readVarInt()))
                .consumerMainThread((packet, context) -> client(context, () -> () -> ClientQuests.dialogueClose(packet.session))).add();
        CHANNEL.messageBuilder(Toast.class, 3).encoder(Toast::encode).decoder(Toast::decode)
                .consumerMainThread((packet, context) -> client(context, () -> () -> ClientQuests.toast(packet))).add();
        CHANNEL.messageBuilder(Markers.class, 4)
                .encoder((p, b) -> b.writeCollection(p.markers, (buf, view) -> view.write(buf)))
                .decoder(b -> new Markers(b.readList(MarkerView::read)))
                .consumerMainThread((packet, context) -> client(context, () -> () -> ClientQuests.markers(packet.markers))).add();
        CHANNEL.messageBuilder(Meta.class, 5).encoder(Meta::encode).decoder(Meta::decode)
                .consumerMainThread((packet, context) -> client(context, () -> () -> ClientQuests.meta(packet))).add();
        CHANNEL.messageBuilder(Quests.class, 6).encoder(Quests::encode).decoder(Quests::decode)
                .consumerMainThread((packet, context) -> client(context, () -> () -> ClientQuests.quests(packet))).add();
        CHANNEL.messageBuilder(LockedRecipes.class, 7)
                .encoder((p, b) -> {
                    b.writeCollection(p.recipes, FriendlyByteBuf::writeResourceLocation);
                    b.writeCollection(p.items, FriendlyByteBuf::writeResourceLocation);
                })
                .decoder(b -> new LockedRecipes(b.readList(FriendlyByteBuf::readResourceLocation),
                        b.readList(FriendlyByteBuf::readResourceLocation)))
                .consumerMainThread((packet, context) -> client(context,
                        () -> () -> com.solace.client.quests.ClientRecipeLocks.set(packet.recipes, packet.items))).add();
    }

    private static void client(Supplier<NetworkEvent.Context> context, Supplier<Runnable> action) {
        // The lambda body is only loaded on a physical client, so client classes never reach a server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> action.get());
        context.get().setPacketHandled(true);
    }

    private static void send(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void intent(int kind, String id, String detail, int a, int b, int c) {
        CHANNEL.sendToServer(new Intent(kind, id, detail, a, b, c));
    }

    public static void toast(ServerPlayer player, String kind, Component title, Component subtitle, String icon) {
        send(player, new Toast(kind, title, subtitle, icon == null ? "" : icon));
    }

    public static void dialogueNode(ServerPlayer player, int session, int sequence, int npcEntity, String item,
                                    Component speaker, Component text, List<Views.ChoiceView> choices,
                                    boolean canAdvance, int autoTicks) {
        send(player, new DialogueNode(session, sequence, npcEntity, item, speaker, text, choices, canAdvance,
                autoTicks));
    }

    public static void dialogueClose(ServerPlayer player, int session) {
        send(player, new DialogueClose(session));
    }

    public static void markers(ServerPlayer player, List<MarkerView> markers) {
        send(player, new Markers(markers));
    }

    static void meta(ServerPlayer player, Meta meta) {
        send(player, meta);
    }

    static void quests(ServerPlayer player, Quests quests) {
        send(player, quests);
    }

    /** The recipes locked for this player (technique notes they have not learned). */
    public static void lockedRecipes(ServerPlayer player, List<ResourceLocation> recipes, List<ResourceLocation> items) {
        send(player, new LockedRecipes(recipes, items));
    }

    record LockedRecipes(List<ResourceLocation> recipes, List<ResourceLocation> items) {
    }

    public static void forget(UUID player) {
        BUCKETS.remove(player);
    }

    private static boolean allow(ServerPlayer player) {
        long now = System.nanoTime();
        double[] bucket = BUCKETS.computeIfAbsent(player.getUUID(), id -> new double[]{20, now});
        bucket[0] = Math.min(20, bucket[0] + (now - bucket[1]) / 1e9 * 10);
        bucket[1] = now;
        if (bucket[0] < 1) {
            return false;
        }
        bucket[0]--;
        return true;
    }

    /** The only client to server packet. Carries no progress, items, text or commands. */
    public record Intent(int kind, String id, String detail, int a, int b, int c) {
        void encode(FriendlyByteBuf buf) {
            buf.writeByte(kind);
            buf.writeUtf(id, 256);
            buf.writeUtf(detail, 256);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
            buf.writeVarInt(c);
        }

        static Intent decode(FriendlyByteBuf buf) {
            return new Intent(buf.readByte(), buf.readUtf(256), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.setPacketHandled(true);
            ServerPlayer player = context.getSender();
            if (player == null || !allow(player)) {
                return;
            }
            switch (kind) {
                case DIALOGUE_CHOICE -> DialogueManager.choose(player, a, b, c);
                case DIALOGUE_ADVANCE -> DialogueManager.advance(player, a, b);
                case DIALOGUE_CLOSE -> DialogueManager.close(player, a);
                default -> {
                    ResourceLocation questId = ResourceLocation.tryParse(id);
                    Quest quest = questId == null ? null : QuestContent.current.quests.get(questId);
                    if (quest == null) {
                        return;
                    }
                    switch (kind) {
                        case CLAIM -> QuestEngine.claim(player, quest, detail.isEmpty() ? null : detail);
                        case TRACK -> {
                            var ctx = QuestEngine.ctx(player);
                            // Only quests the player is actually doing can be tracked.
                            if (a == 0 || QuestLogic.view(ctx, quest) == QuestLogic.View.ACTIVE) {
                                QuestLogic.track(ctx, quest.id(), a != 0);
                            }
                        }
                        case CHECK -> QuestEngine.check(player, quest, detail);
                        case ABANDON -> QuestLogic.abandon(QuestEngine.ctx(player), quest);
                        default -> {
                        }
                    }
                }
            }
        }
    }

    public record DialogueNode(int session, int sequence, int npcEntity, String item, Component speaker,
                               Component text, List<Views.ChoiceView> choices, boolean canAdvance, int autoTicks) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(session);
            buf.writeVarInt(sequence);
            buf.writeInt(npcEntity);
            buf.writeUtf(item, 256);
            buf.writeComponent(speaker);
            buf.writeComponent(text);
            buf.writeCollection(choices, (b, view) -> view.write(b));
            buf.writeBoolean(canAdvance);
            buf.writeVarInt(autoTicks);
        }

        static DialogueNode decode(FriendlyByteBuf buf) {
            return new DialogueNode(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readUtf(256),
                    buf.readComponent(), buf.readComponent(), buf.readList(Views.ChoiceView::read), buf.readBoolean(),
                    buf.readVarInt());
        }
    }

    public record DialogueClose(int session) {
    }

    /** {@code kind}: started, phase, completed, failed, reputation. */
    public record Toast(String kind, Component title, Component subtitle, String icon) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 32);
            buf.writeComponent(title);
            buf.writeComponent(subtitle);
            buf.writeUtf(icon, 300);
        }

        static Toast decode(FriendlyByteBuf buf) {
            return new Toast(buf.readUtf(32), buf.readComponent(), buf.readComponent(), buf.readUtf(300));
        }
    }

    public record Markers(List<MarkerView> markers) {
    }

    /** Chapters, factions and the dialogue log: small, sent whole when anything in it changes. */
    public record Meta(List<ChapterView> chapters, List<FactionView> factions, List<Component> log) {
        void encode(FriendlyByteBuf buf) {
            buf.writeCollection(chapters, (b, view) -> view.write(b));
            buf.writeCollection(factions, (b, view) -> view.write(b));
            buf.writeCollection(log, FriendlyByteBuf::writeComponent);
        }

        static Meta decode(FriendlyByteBuf buf) {
            return new Meta(buf.readList(ChapterView::read), buf.readList(FactionView::read),
                    buf.readList(FriendlyByteBuf::readComponent));
        }
    }

    /** Quest views that changed. {@code reset} drops everything the client knew first. */
    public record Quests(boolean reset, List<QuestView> updates, List<ResourceLocation> removed) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(reset);
            buf.writeCollection(updates, (b, view) -> view.write(b));
            buf.writeCollection(removed, FriendlyByteBuf::writeResourceLocation);
        }

        static Quests decode(FriendlyByteBuf buf) {
            return new Quests(buf.readBoolean(), buf.readList(QuestView::read),
                    buf.readList(FriendlyByteBuf::readResourceLocation));
        }
    }
}
