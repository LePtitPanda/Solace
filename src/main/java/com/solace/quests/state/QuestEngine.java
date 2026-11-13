package com.solace.quests.state;

import com.mojang.logging.LogUtils;
import com.solace.quests.api.QuestApi;
import com.solace.quests.api.QuestEvent;
import com.solace.quests.content.Dialogue;
import com.solace.quests.content.Faction;
import com.solace.quests.content.Quest;
import com.solace.world.gates.StructureGateConfig;
import com.solace.world.gates.StructureGates;
import com.solace.world.gates.StructureLocators;
import com.solace.quests.content.Quest.Objective;
import com.solace.quests.content.Quest.Phase;
import com.solace.quests.content.Quest.Reward;
import com.solace.quests.content.QuestContent;
import com.solace.quests.dialogue.DialogueManager;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.state.PlayerQuestData.Active;
import com.solace.quests.state.PlayerQuestData.QuestState;
import com.solace.quests.state.PlayerQuestData.Status;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Server-side entry point: turns game events into {@link QuestLogic} calls and runs effects. */
public final class QuestEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final java.util.Set<String> CLAIMED_BY_HAND = java.util.Set.of("solace:item", "solace:xp",
            "solace:loot_table");
    private static int effectDepth;

    private QuestEngine() {
    }

    public static ServerCtx ctx(ServerPlayer player) {
        return new ServerCtx(player);
    }

    public static QuestLogic.Events events(ServerPlayer player) {
        return new QuestLogic.Events() {
            @Override
            public void started(Quest quest) {
                if (!QuestLogic.hidden(ctx(player), quest)) {
                    QuestNetwork.toast(player, "started", Component.literal("Quest started"), quest.title(),
                            quest.icon());
                    story(player, quest.phases().get(0));
                }
                post(QuestEvent.Type.QUEST_STARTED, player, quest.id(), "", 0);
            }

            @Override
            public void phase(Quest quest, Phase phase) {
                if (!QuestLogic.hidden(ctx(player), quest)) {
                    QuestNetwork.toast(player, "phase", Component.literal("Quest updated"), quest.title(),
                            quest.icon());
                    story(player, phase);
                }
                post(QuestEvent.Type.PHASE_CHANGED, player, quest.id(), phase.id(), 0);
            }

            @Override
            public void completed(Quest quest) {
                // Only rewards that land in the inventory wait for "Claim rewards". Flags, reputation,
                // unlocks and the like apply now, so other quests never stall on an unclaimed one.
                boolean waiting = false;
                for (Reward reward : quest.rewards()) {
                    if (quest.autoClaim() || !CLAIMED_BY_HAND.contains(reward.type())) {
                        claim(player, quest, reward.id());
                    } else {
                        waiting = true;
                    }
                }
                QuestNetwork.toast(player, "completed",
                        Component.literal(waiting ? "Quest completed - claim rewards in the quest book" : "Quest completed"),
                        quest.title(), quest.icon());
                post(QuestEvent.Type.QUEST_COMPLETED, player, quest.id(), "", 0);
            }

            @Override
            public void failed(Quest quest) {
                QuestNetwork.toast(player, "failed", Component.literal("Quest failed"), quest.title(), quest.icon());
                post(QuestEvent.Type.QUEST_FAILED, player, quest.id(), "", 0);
            }

            @Override
            public void actions(Quest quest, List<Reward> actions) {
                run(ctx(player), actions);
            }
        };
    }

    public static void post(QuestEvent.Type type, ServerPlayer player, ResourceLocation id, String detail, int value) {
        MinecraftForge.EVENT_BUS.post(new QuestEvent(type, player, id, detail, value));
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

    public static List<Active> active(ServerCtx ctx, String type) {
        return List.copyOf(index(ctx).getOrDefault(type, List.of()));
    }

    /**
     * A game event that may progress objectives of {@code type}. Cost: one set lookup when no
     * loaded quest uses the type, otherwise only this player's active objectives of that type.
     * {@code entity} is what the objective's "when" may inspect with a solace:entity condition.
     */
    public static void objective(ServerPlayer player, String type, Predicate<Objective> match, int amount,
                                 Entity entity) {
        if (!QuestContent.current.objectiveTypes.contains(type)) {
            return;
        }
        ServerCtx ctx = ctx(player);
        ctx.entity = entity;
        QuestLogic.Events events = events(player);
        for (Active entry : active(ctx, type)) {
            Objective objective = entry.objective();
            if (match.test(objective) && whenPasses(ctx, entry)) {
                progress(ctx, entry, amount, false, events);
            }
        }
    }

    private static void progress(ServerCtx ctx, Active entry, int value, boolean set, QuestLogic.Events events) {
        QuestState state = ctx.data().quests.get(entry.quest().id());
        String id = entry.objective().id();
        int before = state == null ? 0 : state.progress.getOrDefault(id, 0);
        if (set) {
            QuestLogic.setProgress(ctx, entry.quest(), id, value, events);
        } else {
            QuestLogic.addProgress(ctx, entry.quest(), id, value, events);
        }
        int after = state == null ? 0 : state.progress.getOrDefault(id, 0);
        if (after != before) {
            post(QuestEvent.Type.OBJECTIVE_PROGRESSED, ctx.player, entry.quest().id(), id, after);
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
        // Every 5th visit (5 s): mirror vanilla gossip into "villager gossip" factions.
        if (!content.factions.isEmpty() && (player.tickCount / 20) % 5 == 0) {
            Factions.refreshGossip(ctx);
        }
        if (ctx.data().quests.isEmpty() && content.autoStart.isEmpty() && ctx.data().markers.isEmpty()) {
            return;
        }
        QuestLogic.Events events = events(player);
        for (Active entry : active(ctx, "solace:item")) {
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
            progress(ctx, entry, have, true, events);
        }
        for (Active entry : active(ctx, "solace:reach_location")) {
            Objective objective = entry.objective();
            if (objective.ref().toString().equals(ctx.dimension())
                    && player.distanceToSqr(objective.x() + 0.5, objective.y() + 0.5, objective.z() + 0.5)
                    <= (double) objective.radius() * objective.radius() && whenPasses(ctx, entry)) {
                progress(ctx, entry, 1, true, events);
            }
        }
        QuestLogic.evaluate(ctx, events);
        // Every other visit (2 s): moving marker targets.
        if ((player.tickCount / 20) % 2 == 0) {
            Markers.sync(ctx);
        }
    }

    /** Player intent "tick this off": only checkmark objectives of the current phase accept it. */
    public static boolean check(ServerPlayer player, Quest quest, String objectiveId) {
        ServerCtx ctx = ctx(player);
        for (Active entry : active(ctx, "solace:checkmark")) {
            if (entry.quest() == quest && entry.objective().id().equals(objectiveId) && whenPasses(ctx, entry)) {
                progress(ctx, entry, 1, true, events(player));
                return true;
            }
        }
        return false;
    }

    /** The "solace:deliver" action: hands items over for the deliver objectives bound to this NPC. */
    public static void deliver(ServerPlayer player, Dialogue.NpcBinding binding) {
        ServerCtx ctx = ctx(player);
        QuestLogic.Events events = events(player);
        for (Active entry : active(ctx, "solace:deliver")) {
            Objective objective = entry.objective();
            if (binding.id().equals(objective.ref()) && whenPasses(ctx, entry)
                    && ctx.countItem(objective.target(), false) >= objective.count()) {
                player.getInventory().clearOrCountMatchingItems(objective.target()::matches, objective.count(),
                        player.inventoryMenu.getCraftSlots());
                progress(ctx, entry, objective.count(), true, events);
            }
        }
    }

    /** Claims one reward, or all with {@code rewardId == null}. Returns how many were handed out. */
    public static int claim(ServerPlayer player, Quest quest, String rewardId) {
        ServerCtx ctx = ctx(player);
        List<Reward> rewards = QuestLogic.claim(ctx, quest, rewardId);
        run(ctx, rewards);
        if (!rewards.isEmpty()) {
            QuestLogic.evaluate(ctx, events(player));
        }
        return rewards.size();
    }

    /** Runs rewards or actions in order. One failing effect is logged and does not stop the rest. */
    public static void run(ServerCtx ctx, List<Reward> effects) {
        // Content can chain effects into each other (an action starting a quest whose on_enter
        // runs an action...). Cut runaway chains instead of overflowing the stack.
        if (effectDepth >= 8) {
            LOGGER.error("[Solace Quests] Effect chain deeper than 8 for {}; skipping {} effect(s). "
                    + "Check for actions that trigger each other.", ctx.player.getGameProfile().getName(),
                    effects.size());
            return;
        }
        effectDepth++;
        try {
            for (Reward effect : effects) {
                try {
                    apply(ctx, effect);
                } catch (RuntimeException exception) {
                    LOGGER.error("[Solace Quests] Effect {} ({}) failed for {}", effect.type(), effect.id(),
                            ctx.player.getGameProfile().getName(), exception);
                }
            }
        } finally {
            effectDepth--;
        }
    }

    private static void apply(ServerCtx ctx, Reward effect) {
        ServerPlayer player = ctx.player;
        QuestContent content = ctx.content();
        QuestLogic.Events events = events(player);
        Quest quest = effect.target() == null ? null : content.quests.get(effect.target());
        switch (effect.type()) {
            case "solace:item" -> {
                Item item = ForgeRegistries.ITEMS.getValue(effect.target());
                if (item == null || item == Items.AIR) {
                    LOGGER.warn("[Solace Quests] Item {} does not exist", effect.target());
                    return;
                }
                int left = effect.count();
                while (left > 0) {
                    ItemStack stack = new ItemStack(item, Math.min(left, item.getMaxStackSize()));
                    if (effect.nbt() != null) {
                        stack.setTag(effect.nbt().copy());
                    }
                    left -= stack.getCount();
                    // Overflow is dropped at the player's feet.
                    ItemHandlerHelper.giveItemToPlayer(player, stack);
                }
            }
            case "solace:take_item" -> player.getInventory().clearOrCountMatchingItems(
                    stack -> effect.target().equals(ForgeRegistries.ITEMS.getKey(stack.getItem())), effect.count(),
                    player.inventoryMenu.getCraftSlots());
            case "solace:xp" -> {
                if (effect.on()) {
                    player.giveExperienceLevels(effect.count());
                } else {
                    player.giveExperiencePoints(effect.count());
                }
            }
            case "solace:loot_table" -> {
                LootTable table = player.server.getLootData().getLootTable(effect.target());
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
                        .withPermission(effect.count()).withSuppressedOutput();
                player.server.getCommands().performPrefixedCommand(source,
                        effect.name().replaceAll("@p\\b", player.getStringUUID()));
            }
            case "solace:flag" -> {
                if (effect.on()) {
                    ctx.data().flags.add(effect.name());
                } else {
                    ctx.data().flags.remove(effect.name());
                }
                ctx.changed();
            }
            case "solace:variable" -> {
                ctx.data().variables.merge(effect.name(), effect.count(), (old, value) -> effect.on() ? old + value : value);
                ctx.changed();
            }
            case "solace:unlock" -> {
                ctx.data().unlocked.add(effect.target());
                ctx.changed();
            }
            case "solace:reputation" -> {
                Faction faction = content.factions.get(effect.target());
                if (faction != null) {
                    Factions.add(ctx, faction, effect.count(), true);
                }
            }
            case "solace:start_quest" -> {
                // Ignores locks (the author asked for it) but never restarts a running or finished quest.
                QuestLogic.View view = quest == null ? null : QuestLogic.view(ctx, quest);
                if (view == QuestLogic.View.AVAILABLE || view == QuestLogic.View.LOCKED) {
                    QuestLogic.start(ctx, quest, true, events);
                }
            }
            case "solace:complete_quest", "solace:fail_quest" -> {
                if (quest != null) {
                    QuestLogic.move(ctx, quest, effect.type().equals("solace:fail_quest") ? Quest.FAIL : Quest.COMPLETE,
                            "action", events);
                }
            }
            case "solace:set_phase" -> {
                if (quest != null) {
                    QuestLogic.move(ctx, quest, effect.name(), "action", events);
                }
            }
            case "solace:complete_objective" -> {
                if (quest != null) {
                    QuestLogic.setProgress(ctx, quest, effect.name(), Integer.MAX_VALUE, events);
                }
            }
            case "solace:dialogue" -> {
                Dialogue dialogue = content.dialogues.get(effect.target());
                if (dialogue != null) {
                    DialogueManager.open(player, dialogue, DialogueManager.npc(player), null, null);
                }
            }
            case "solace:deliver" -> {
                Dialogue.NpcBinding binding = content.npcs.get(effect.target());
                if (binding != null) {
                    deliver(player, binding);
                }
            }
            case "solace:close" -> DialogueManager.close(player);
            case "solace:add_marker" -> {
                if (ctx.data().markers.add(effect.target())) {
                    ctx.changed();
                }
            }
            case "solace:remove_marker" -> {
                if (ctx.data().markers.remove(effect.target())) {
                    ctx.changed();
                }
            }
            case "solace:custom" -> {
                QuestApi.EffectHandler handler = QuestApi.EFFECTS.get(effect.name());
                if (handler == null) {
                    LOGGER.warn("[Solace Quests] No custom effect handler \"{}\" is registered", effect.name());
                } else {
                    handler.run(player, effect.data());
                }
            }
            // World-wide and idempotent: the first player to reach this opens it for everyone.
            case "solace:open_structure_gate" -> {
                if (StructureGates.open(player.server, effect.name(), StructureGateConfig.Mode.parse(effect.data())) < 0) {
                    LOGGER.warn("[Solace Quests] {} is not in config/solace-structure-gates.json", effect.name());
                }
            }
            case "solace:close_structure_gate" -> {
                if (StructureGates.close(player.server, effect.name()) < 0) {
                    LOGGER.warn("[Solace Quests] {} is not in config/solace-structure-gates.json", effect.name());
                }
            }
            case "solace:give_structure_map" -> StructureLocators.giveMap(player, effect.name(), effect.data());
            case "solace:give_structure_compass" -> {
                Item item = ForgeRegistries.ITEMS.getValue(effect.target());
                StructureLocators.giveCompass(player, effect.name(),
                        item == null || item == Items.AIR ? Items.COMPASS : item, effect.data());
            }
            default -> LOGGER.warn("[Solace Quests] Unknown effect type {}", effect.type());
        }
    }

    public static Quest quest(ResourceLocation id) {
        return QuestContent.current.quests.get(id);
    }
}
