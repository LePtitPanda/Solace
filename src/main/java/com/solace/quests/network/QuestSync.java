package com.solace.quests.network;

import com.solace.quests.content.Chapter;
import com.solace.quests.content.Faction;
import com.solace.quests.content.Quest;
import com.solace.quests.content.Quest.Objective;
import com.solace.quests.content.Quest.Phase;
import com.solace.quests.content.Quest.Reward;
import com.solace.quests.content.QuestContent;
import com.solace.quests.content.Target;
import com.solace.quests.network.Views.ChapterView;
import com.solace.quests.network.Views.FactionView;
import com.solace.quests.network.Views.ObjectiveView;
import com.solace.quests.network.Views.QuestView;
import com.solace.quests.network.Views.RewardView;
import com.solace.quests.state.Factions;
import com.solace.quests.state.PlayerQuestData;
import com.solace.quests.state.PlayerQuestData.QuestState;
import com.solace.quests.state.PlayerQuestData.Status;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.QuestLogic;
import com.solace.quests.state.ServerCtx;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps each client's quest book in step with the server. A full snapshot goes out on login and
 * reload; afterwards only the quest views whose encoded bytes changed are sent, at most every
 * few ticks and only for players whose state was touched.
 */
public final class QuestSync {
    private static final int BATCH = 40;
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Map<UUID, Map<ResourceLocation, Integer>> SENT = new HashMap<>();
    private static final Map<UUID, Integer> META = new HashMap<>();
    private static final Map<UUID, QuestContent> CONTENT = new HashMap<>();

    private QuestSync() {
    }

    public static void dirty(ServerPlayer player) {
        DIRTY.add(player.getUUID());
    }

    public static void forget(UUID player) {
        DIRTY.remove(player);
        SENT.remove(player);
        META.remove(player);
        CONTENT.remove(player);
    }

    /** Called every few server ticks. */
    public static void flush(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (DIRTY.remove(id) || CONTENT.get(id) != QuestContent.current) {
                send(player);
            }
        }
    }

    private static void send(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerCtx ctx = QuestEngine.ctx(player);
        QuestContent content = ctx.content();
        boolean reset = CONTENT.put(id, content) != content;
        if (reset) {
            SENT.remove(id);
            META.remove(id);
        }

        Set<ResourceLocation> openChapters = new HashSet<>();
        List<ChapterView> chapters = new ArrayList<>();
        for (Chapter chapter : content.chapters.values()) {
            boolean open = QuestLogic.chapterOpen(ctx, chapter.id());
            if (open || !chapter.hiddenUntilUnlocked()) {
                openChapters.add(chapter.id());
                chapters.add(new ChapterView(chapter.id(), chapter.title(), text(chapter.description()),
                        chapter.icon(), chapter.order()));
            }
        }
        List<FactionView> factions = new ArrayList<>();
        for (Faction faction : content.factions.values()) {
            int value = Factions.get(ctx, faction.id());
            int rank = faction.rankIndex(value);
            factions.add(new FactionView(faction.id(), faction.name(), faction.color(), faction.icon(), value,
                    faction.ranks().get(rank).name(), Math.max(faction.min(), faction.ranks().get(rank).at()),
                    rank + 1 < faction.ranks().size() ? faction.ranks().get(rank + 1).at() : faction.max()));
        }
        List<Component> log = new ArrayList<>();
        for (String line : ctx.data().log) {
            try {
                log.add(Component.Serializer.fromJson(line));
            } catch (RuntimeException ignored) {
            }
        }
        QuestNetwork.Meta meta = new QuestNetwork.Meta(chapters, factions, log);
        FriendlyByteBuf metaBytes = new FriendlyByteBuf(Unpooled.buffer());
        meta.encode(metaBytes);
        int metaHash = hash(metaBytes);
        if (!Integer.valueOf(metaHash).equals(META.put(id, metaHash))) {
            QuestNetwork.meta(player, meta);
        }

        Map<ResourceLocation, Integer> sent = SENT.computeIfAbsent(id, key -> new HashMap<>());
        Set<ResourceLocation> seen = new HashSet<>();
        List<QuestView> updates = new ArrayList<>();
        for (Quest quest : content.quests.values()) {
            QuestView view = openChapters.contains(quest.chapter()) ? view(ctx, quest) : null;
            if (view == null) {
                continue;
            }
            seen.add(quest.id());
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            view.write(bytes);
            int viewHash = hash(bytes);
            if (!Integer.valueOf(viewHash).equals(sent.put(quest.id(), viewHash))) {
                updates.add(view);
            }
        }
        List<ResourceLocation> removed = new ArrayList<>(sent.keySet());
        removed.removeAll(seen);
        sent.keySet().retainAll(seen);
        if (!reset && updates.isEmpty() && removed.isEmpty()) {
            return;
        }
        // Batched so a big pack's first snapshot stays far below the packet size limit.
        for (int from = 0; from == 0 || from < updates.size(); from += BATCH) {
            QuestNetwork.quests(player, new QuestNetwork.Quests(reset && from == 0,
                    updates.subList(from, Math.min(updates.size(), from + BATCH)), from == 0 ? removed : List.of()));
        }
    }

    private static int hash(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return java.util.Arrays.hashCode(bytes);
    }

    private static Component text(Component component) {
        return component == null ? Component.empty() : component;
    }

    /** The quest as this player may see it, or null when it is hidden from them. */
    private static QuestView view(ServerCtx ctx, Quest quest) {
        QuestLogic.View status = QuestLogic.view(ctx, quest);
        ctx.quest = null;
        if (quest.hidden() && status == QuestLogic.View.LOCKED
                || quest.visibleWhen() != null && !quest.visibleWhen().test(ctx)) {
            return null;
        }
        PlayerQuestData data = ctx.data();
        QuestState state = data.quests.get(quest.id());
        boolean active = status == QuestLogic.View.ACTIVE;
        // Running: the current phase. Not started yet: a preview of the first one. Finished: nothing.
        Phase phase = active ? quest.phase(state.phase)
                : status == QuestLogic.View.LOCKED || status == QuestLogic.View.AVAILABLE ? quest.phases().get(0) : null;
        List<ObjectiveView> objectives = new ArrayList<>();
        if (phase != null) {
            for (Objective objective : phase.objectives()) {
                if (!objective.hidden()) {
                    objectives.add(new ObjectiveView(objective.id(),
                            objective.text() != null ? objective.text() : describe(objective), objective.icon(),
                            active ? state.progress.getOrDefault(objective.id(), 0) : 0, objective.count(),
                            objective.optional(), objective.type().equals("solace:checkmark")));
                }
            }
        }
        // Story so far: the text of every phase on the path the player actually took.
        List<Component> story = new ArrayList<>();
        if (state != null) {
            Set<String> visited = new LinkedHashSet<>();
            state.history.forEach(entry -> visited.add(entry.phase()));
            for (String phaseId : visited) {
                Phase taken = quest.phase(phaseId);
                if (taken != null && taken.story() != null) {
                    story.add(taken.story());
                }
            }
        }
        List<RewardView> rewards = new ArrayList<>();
        for (Reward reward : quest.rewards()) {
            Component text = reward.text() != null ? reward.text() : describe(ctx.content(), reward);
            if (text != null) {
                rewards.add(new RewardView(reward.id(), text, reward.icon() != null ? reward.icon() : icon(ctx, reward),
                        state != null && state.status == Status.COMPLETED && state.claimed.contains(reward.id())));
            }
        }
        int secondsLeft = active && quest.timeLimitSeconds() > 0
                ? (int) Math.max(0, (state.startedAt + quest.timeLimitSeconds() * 20L - ctx.gameTime()) / 20) : -1;
        return new QuestView(quest.id(), quest.chapter(), quest.title(), text(quest.description()), quest.icon(),
                quest.x(), quest.y(), quest.requires(), status.ordinal(), quest.optional(), story, objectives, rewards,
                data.tracked.contains(quest.id()), quest.abandonable(), secondsLeft);
    }

    private static Component name(Target target, String registry) {
        if (target == null) {
            return Component.literal("?");
        }
        if (target.tag()) {
            return Component.literal("#" + target.id().getPath().replace('_', ' '));
        }
        switch (registry) {
            case "item" -> {
                Item item = ForgeRegistries.ITEMS.getValue(target.id());
                if (item != null) {
                    return new ItemStack(item).getHoverName();
                }
            }
            case "block" -> {
                Block block = ForgeRegistries.BLOCKS.getValue(target.id());
                if (block != null) {
                    return block.getName();
                }
            }
            case "entity" -> {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(target.id());
                if (type != null) {
                    return type.getDescription();
                }
            }
            default -> {
            }
        }
        return Component.literal(target.id().getPath().replace('_', ' ').replace('/', ' '));
    }

    private static Component path(ResourceLocation id) {
        return Component.literal(id == null ? "?" : id.getPath().replace('_', ' ').replace('/', ' '));
    }

    /** Fallback wording for an objective without "text". */
    private static Component describe(Objective objective) {
        Target target = objective.target();
        return switch (objective.type()) {
            case "solace:kill" -> Component.literal("Defeat ").append(name(target, "entity"));
            case "solace:interact_entity" -> Component.literal("Interact with ").append(name(target, "entity"));
            case "solace:break_block" -> Component.literal("Break ").append(name(target, "block"));
            case "solace:place_block" -> Component.literal("Place ").append(name(target, "block"));
            case "solace:interact_block" -> Component.literal("Use ").append(name(target, "block"));
            case "solace:item" -> Component.literal(objective.consume() ? "Hand in " : "Collect ")
                    .append(name(target, "item"));
            case "solace:deliver" -> Component.literal("Deliver ").append(name(target, "item"));
            case "solace:craft" -> Component.literal("Craft ").append(name(target, "item"));
            case "solace:smelt" -> Component.literal("Smelt ").append(name(target, "item"));
            case "solace:use_item" -> Component.literal("Use ").append(name(target, "item"));
            case "solace:reach_location" -> Component.literal("Reach " + objective.x() + ", " + objective.y() + ", "
                    + objective.z());
            case "solace:find_structure" -> Component.literal("Find ").append(name(target, "structure"));
            case "solace:enter_biome" -> Component.literal("Enter ").append(name(target, "biome"));
            case "solace:enter_dimension" -> Component.literal("Enter ").append(path(objective.ref()));
            case "solace:dialogue_node" -> Component.literal("Talk");
            case "solace:advancement" -> Component.literal("Earn ").append(path(objective.ref()));
            case "solace:reputation", "solace:faction_rank" -> Component.literal("Earn standing with ")
                    .append(path(objective.ref()));
            case "solace:checkmark" -> Component.literal("Confirm");
            default -> Component.literal(objective.id().replace('_', ' '));
        };
    }

    /** Fallback wording for a reward without "text"; null hides bookkeeping rewards (flags, commands...). */
    private static Component describe(QuestContent content, Reward reward) {
        return switch (reward.type()) {
            case "solace:item" -> Component.literal(reward.count() + "x ")
                    .append(name(new Target(false, reward.target()), "item"));
            case "solace:xp" -> Component.literal(reward.count() + (reward.on() ? " levels" : " XP"));
            case "solace:loot_table" -> Component.literal("Loot");
            case "solace:reputation" -> {
                Faction faction = content.factions.get(reward.target());
                yield Component.literal((reward.count() > 0 ? "+" : "") + reward.count() + " ")
                        .append(faction == null ? path(reward.target()) : faction.name());
            }
            default -> null;
        };
    }

    private static String icon(ServerCtx ctx, Reward reward) {
        return switch (reward.type()) {
            case "solace:item" -> "item:" + reward.target();
            case "solace:xp" -> "item:minecraft:experience_bottle";
            case "solace:loot_table" -> "item:minecraft:chest";
            case "solace:reputation" -> {
                Faction faction = ctx.content().factions.get(reward.target());
                yield faction == null ? null : faction.icon();
            }
            default -> null;
        };
    }
}
