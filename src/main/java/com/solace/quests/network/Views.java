package com.solace.quests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What the client is told. These are per-player views built on the server, not the content
 * definitions: hidden quests, future phases, conditions and dialogue trees never leave the server.
 */
public final class Views {
    private Views() {
    }

    private static void writeIcon(FriendlyByteBuf buf, String icon) {
        buf.writeUtf(icon == null ? "" : icon, 300);
    }

    public record ChapterView(ResourceLocation id, Component title, Component description, String icon, int order) {
        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeComponent(title);
            buf.writeComponent(description);
            writeIcon(buf, icon);
            buf.writeVarInt(order);
        }

        public static ChapterView read(FriendlyByteBuf buf) {
            return new ChapterView(buf.readResourceLocation(), buf.readComponent(), buf.readComponent(),
                    buf.readUtf(300), buf.readVarInt());
        }
    }

    public record ObjectiveView(String id, Component text, String icon, int progress, int count, boolean optional,
                                boolean checkmark) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256);
            buf.writeComponent(text);
            writeIcon(buf, icon);
            buf.writeVarInt(progress);
            buf.writeVarInt(count);
            buf.writeBoolean(optional);
            buf.writeBoolean(checkmark);
        }

        public static ObjectiveView read(FriendlyByteBuf buf) {
            return new ObjectiveView(buf.readUtf(256), buf.readComponent(), buf.readUtf(300), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }
    }

    public record RewardView(String id, Component text, String icon, boolean claimed) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256);
            buf.writeComponent(text);
            writeIcon(buf, icon);
            buf.writeBoolean(claimed);
        }

        public static RewardView read(FriendlyByteBuf buf) {
            return new RewardView(buf.readUtf(256), buf.readComponent(), buf.readUtf(300), buf.readBoolean());
        }
    }

    /** {@code status} is a QuestLogic.View ordinal: locked, available, active, claimable, completed, failed. */
    public record QuestView(ResourceLocation id, ResourceLocation chapter, Component title, Component description,
                            String icon, int x, int y, List<ResourceLocation> requires, int status, boolean optional,
                            List<Component> story, List<ObjectiveView> objectives, List<RewardView> rewards,
                            boolean tracked, boolean abandonable, int secondsLeft) {
        public static final int LOCKED = 0, AVAILABLE = 1, ACTIVE = 2, CLAIMABLE = 3, COMPLETED = 4, FAILED = 5;

        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeResourceLocation(chapter);
            buf.writeComponent(title);
            buf.writeComponent(description);
            writeIcon(buf, icon);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeCollection(requires, FriendlyByteBuf::writeResourceLocation);
            buf.writeByte(status);
            buf.writeBoolean(optional);
            buf.writeCollection(story, FriendlyByteBuf::writeComponent);
            buf.writeCollection(objectives, (b, view) -> view.write(b));
            buf.writeCollection(rewards, (b, view) -> view.write(b));
            buf.writeBoolean(tracked);
            buf.writeBoolean(abandonable);
            buf.writeVarInt(secondsLeft);
        }

        public static QuestView read(FriendlyByteBuf buf) {
            return new QuestView(buf.readResourceLocation(), buf.readResourceLocation(), buf.readComponent(),
                    buf.readComponent(), buf.readUtf(300), buf.readVarInt(), buf.readVarInt(),
                    buf.readList(FriendlyByteBuf::readResourceLocation), buf.readByte(), buf.readBoolean(),
                    buf.readList(FriendlyByteBuf::readComponent), buf.readList(ObjectiveView::read),
                    buf.readList(RewardView::read), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }
    }

    /** {@code floor}/{@code next} are the standing where the current rank starts and the next one begins. */
    public record FactionView(ResourceLocation id, Component name, int color, String icon, int value,
                              Component rank, int floor, int next) {
        public void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeComponent(name);
            buf.writeInt(color);
            writeIcon(buf, icon);
            buf.writeVarInt(value);
            buf.writeComponent(rank);
            buf.writeVarInt(floor);
            buf.writeVarInt(next);
        }

        public static FactionView read(FriendlyByteBuf buf) {
            return new FactionView(buf.readResourceLocation(), buf.readComponent(), buf.readInt(), buf.readUtf(300),
                    buf.readVarInt(), buf.readComponent(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /** {@code y == NO_Y} when only the horizontal position is known (located structures). */
    public record MarkerView(String id, String dimension, int x, int y, int z, Component label, String color,
                             boolean beam) {
        public static final int NO_Y = Integer.MIN_VALUE;

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 300);
            buf.writeUtf(dimension, 256);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeComponent(label);
            buf.writeUtf(color, 32);
            buf.writeBoolean(beam);
        }

        public static MarkerView read(FriendlyByteBuf buf) {
            return new MarkerView(buf.readUtf(300), buf.readUtf(256), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readComponent(), buf.readUtf(32), buf.readBoolean());
        }
    }

    public record ChoiceView(int index, Component text, boolean enabled) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(index);
            buf.writeComponent(text);
            buf.writeBoolean(enabled);
        }

        public static ChoiceView read(FriendlyByteBuf buf) {
            return new ChoiceView(buf.readVarInt(), buf.readComponent(), buf.readBoolean());
        }
    }
}
