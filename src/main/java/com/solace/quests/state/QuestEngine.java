package com.solace.quests.state;

import com.mojang.logging.LogUtils;
import com.solace.quests.content.Quest;
import com.solace.quests.content.Quest.Objective;
import com.solace.quests.content.Quest.Phase;
import com.solace.quests.content.Quest.Reward;
import com.solace.quests.content.QuestContent;
import com.solace.quests.state.PlayerQuestData.Active;
import com.solace.quests.state.PlayerQuestData.QuestState;
import com.solace.quests.state.PlayerQuestData.Status;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Server-side entry point: turns game events into {@link QuestLogic} calls and hands out rewards. */
public final class QuestEngine {
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestEngine() {
    }

    public static ServerCtx ctx(ServerPlayer player) {
        return new ServerCtx(player);
    }

    /** Player feedback. Chat lines for now; toasts and the HUD replace them with the quest book. */
    public static QuestLogic.Events events(ServerPlayer player) {
        return new QuestLogic.Events() {
            @Override
            public void started(Quest quest) {
                say(player, "Quest started: ", quest.title(), ChatFormatting.GOLD);
                story(player, quest.phases().get(0));
            }

            @Override
            public void phase(Quest quest, Phase phase) {
                story(player, phase);
            }

            @Override
            public void completed(Quest quest) {
                say(player, "Quest completed: ", quest.title(), ChatFormatting.GREEN);
                if (quest.autoClaim()) {
                    claim(player, quest, null);
                } else if (!quest.rewards().isEmpty()) {
                    player.sendSystemMessage(Component.literal("Rewards ready: /sq claim " + quest.id())
                            .withStyle(ChatFormatting.GRAY));
                }
            }

            @Override
            public void failed(Quest quest) {
                say(player, "Quest failed: ", quest.title(), ChatFormatting.RED);
            }
        };
    }

    private static void say(ServerPlayer player, String prefix, Component title, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(prefix).withStyle(color)
                .append(title.copy().withStyle(ChatFormatting.WHITE)));
    }

    private static void story(ServerPlayer player, Phase phase) {
        if (phase.story() != null) {
            player.sendSystemMessage(phase.story().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static Map<String, List<Active>> index(ServerCtx ctx) {
        PlayerQuestData data = ctx.data();
        if (data.index == null || data.indexContent != ctx.content()) {
            Map<String, List<Active>> index = new HashMap<>();
            data.quests.forEach((id, state) -> {
                Quest quest = ctx.content().quests.get(id);
                Phase phase = quest == null || state.status != Status.ACTIVE ? null : quest.phase(state.phase);
                if (phase != null) {
                    for (Objective objective : phase.objectives()) {
                        index.computeIfAbsent(objective.type(), type -> new ArrayList<>())
                                .add(new Active(quest, objective));
                    }
                }
            });
            data.index = index;
            data.indexContent = ctx.content();
        }
        return data.index;
    }

    /**
     * A game event that may progress objectives of {@code type}. Cost: one set lookup when no
     * loaded quest uses the type, otherwise only this player's active objectives of that type.
     */
    public static void objective(ServerPlayer player, String type, Predicate<Objective> match, int amount) {
        if (!QuestContent.current.objectiveTypes.contains(type)) {
            return;
        }
        ServerCtx ctx = ctx(player);
        List<Active> active = index(ctx).get(type);
        if (active == null) {
            return;
        }
        QuestLogic.Events events = events(player);
        for (Active entry : List.copyOf(active)) {
            Objective objective = entry.objective();
            if (match.test(objective) && whenPasses(ctx, entry)) {
                QuestLogic.addProgress(ctx, entry.quest(), objective.id(), amount, events);
            }
        }
    }

    private static boolean whenPasses(ServerCtx ctx, Active entry) {
        if (entry.objective().when() == null) {
            return true;
        }
        ctx.quest = entry.quest().id();
        boolean passes = entry.objective().when().test(ctx);
        ctx.quest = null;
        return passes;
    }

    /** Once a second per player: inventory and position objectives, then time-dependent transitions. */
    public static void tick(ServerPlayer player) {
        QuestContent content = QuestContent.current;
        ServerCtx ctx = ctx(player);
        if (ctx.data().quests.isEmpty() && content.autoStart.isEmpty()) {
            return;
        }
        QuestLogic.Events events = events(player);
        for (Active entry : List.copyOf(index(ctx).getOrDefault("solace:item", List.of()))) {
            Objective objective = entry.objective();
            QuestState state = ctx.data().quests.get(entry.quest().id());
            // Latched: once complete, dropping the items does not undo it.
            if (state == null || state.progress.getOrDefault(objective.id(), 0) >= objective.count()
                    || !whenPasses(ctx, entry)) {
                continue;
            }
            int have = Math.min(objective.count(), ctx.countItem(objective.target(), false));
            if (have >= objective.count() && objective.consume()) {
                player.getInventory().clearOrCountMatchingItems(objective.target()::matches, objective.count(),
                        player.inventoryMenu.getCraftSlots());
            }
            QuestLogic.setProgress(ctx, entry.quest(), objective.id(), have, events);
        }
        for (Active entry : List.copyOf(index(ctx).getOrDefault("solace:reach_location", List.of()))) {
            Objective objective = entry.objective();
            if (objective.dimension().toString().equals(ctx.dimension())
                    && player.distanceToSqr(objective.x() + 0.5, objective.y() + 0.5, objective.z() + 0.5)
                    <= (double) objective.radius() * objective.radius() && whenPasses(ctx, entry)) {
                QuestLogic.setProgress(ctx, entry.quest(), objective.id(), 1, events);
            }
        }
        QuestLogic.evaluate(ctx, events);
    }

    /** Claims one reward, or all with {@code rewardId == null}. Returns how many were handed out. */
    public static int claim(ServerPlayer player, Quest quest, String rewardId) {
        ServerCtx ctx = ctx(player);
        List<Reward> rewards = QuestLogic.claim(ctx, quest, rewardId);
        for (Reward reward : rewards) {
            try {
                grant(ctx, reward);
            } catch (RuntimeException exception) {
                LOGGER.error("[Solace Quests] Reward {} of {} failed for {}", reward.id(), quest.id(),
                        player.getGameProfile().getName(), exception);
            }
        }
        if (!rewards.isEmpty()) {
            QuestLogic.evaluate(ctx, events(player));
        }
        return rewards.size();
    }

    private static void grant(ServerCtx ctx, Reward reward) {
        ServerPlayer player = ctx.player;
        switch (reward.type()) {
            case "solace:item" -> {
                Item item = ForgeRegistries.ITEMS.getValue(reward.target());
                if (item == null || item == Items.AIR) {
                    LOGGER.warn("[Solace Quests] Reward item {} does not exist", reward.target());
                    return;
                }
                int left = reward.count();
                while (left > 0) {
                    ItemStack stack = new ItemStack(item, Math.min(left, item.getMaxStackSize()));
                    if (reward.nbt() != null) {
                        stack.setTag(reward.nbt().copy());
                    }
                    left -= stack.getCount();
                    // Overflow is dropped at the player's feet.
                    ItemHandlerHelper.giveItemToPlayer(player, stack);
                }
            }
            case "solace:xp" -> {
                if (reward.on()) {
                    player.giveExperienceLevels(reward.count());
                } else {
                    player.giveExperiencePoints(reward.count());
                }
            }
            case "solace:loot_table" -> {
                LootTable table = player.server.getLootData().getLootTable(reward.target());
                LootParams params = new LootParams.Builder(player.serverLevel())
                        .withParameter(LootContextParams.ORIGIN, player.position())
                        .withParameter(LootContextParams.THIS_ENTITY, player)
                        .create(LootContextParamSets.GIFT);
                table.getRandomItems(params).forEach(stack -> ItemHandlerHelper.giveItemToPlayer(player, stack));
            }
            case "solace:command" -> {
                // Runs as the server, never as the player. "@p" becomes the UUID (a valid entity
                // argument), not the name, so a crafted player name cannot inject command text.
                CommandSourceStack source = player.server.createCommandSourceStack()
                        .withPosition(player.position()).withLevel(player.serverLevel())
                        .withPermission(reward.count()).withSuppressedOutput();
                player.server.getCommands().performPrefixedCommand(source,
                        reward.name().replaceAll("@p\\b", player.getStringUUID()));
            }
            case "solace:flag" -> {
                if (reward.on()) {
                    ctx.data().flags.add(reward.name());
                } else {
                    ctx.data().flags.remove(reward.name());
                }
                ctx.changed();
            }
            case "solace:variable" -> {
                ctx.data().variables.merge(reward.name(), reward.count(), (old, value) -> reward.on() ? old + value : value);
                ctx.changed();
            }
            case "solace:unlock" -> {
                ctx.data().unlocked.add(reward.target());
                ctx.changed();
            }
            default -> LOGGER.warn("[Solace Quests] Unknown reward type {}", reward.type());
        }
    }

    public static Quest quest(ResourceLocation id) {
        return QuestContent.current.quests.get(id);
    }
}
