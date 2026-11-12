package com.solace.quests.dialogue;

import com.solace.quests.api.QuestEvent;
import com.solace.quests.content.Dialogue;
import com.solace.quests.content.Dialogue.Choice;
import com.solace.quests.content.Dialogue.Node;
import com.solace.quests.content.Dialogue.NpcBinding;
import com.solace.quests.content.Quest.Reward;
import com.solace.quests.content.QuestContent;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.network.Views;
import com.solace.quests.state.PlayerQuestData;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.QuestLogic;
import com.solace.quests.state.ServerCtx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned dialogue sessions, one per player, private to that player. The client only ever
 * sees the node it is on and sends back "choice N of session S"; everything is re-checked here.
 */
public final class DialogueManager {
    private static final class Session {
        int id;
        Dialogue dialogue;
        Node node;
        UUID npc;
        boolean freeze;
        int maxDistance = 8;
        int sequence;
        /** Indices into node.choices() that were offered to the client. */
        final List<Integer> offered = new ArrayList<>();
        long autoAt = -1;
        QuestContent content;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextId = 1;

    private DialogueManager() {
    }

    /** Right-click on an entity. Returns true when an NPC binding matched and vanilla interaction must be cancelled. */
    public static boolean interact(ServerPlayer player, Entity target) {
        ServerCtx ctx = QuestEngine.ctx(player);
        ctx.entity = target;
        for (NpcBinding binding : ctx.content().bindings) {
            if (!binding.entity().matches(target) || binding.when() != null && !binding.when().test(ctx)) {
                continue;
            }
            // Remember where this NPC was seen, for "npc" markers.
            ctx.data().positions.put("npc:" + binding.id(), target.level().dimension().location() + " "
                    + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ());
            ctx.changed();
            for (Dialogue.Bound bound : binding.dialogues()) {
                Dialogue dialogue = ctx.content().dialogues.get(bound.dialogue());
                if (dialogue != null && (bound.when() == null || bound.when().test(ctx))) {
                    open(player, dialogue, target, null, binding);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean open(ServerPlayer player, Dialogue dialogue, Entity npc, String node, NpcBinding binding) {
        ServerCtx ctx = QuestEngine.ctx(player);
        ctx.entity = npc;
        String start = node;
        if (start == null) {
            for (Dialogue.Start entry : dialogue.start()) {
                if (entry.when() == null || entry.when().test(ctx)) {
                    start = entry.node();
                    break;
                }
            }
        }
        if (start == null || !dialogue.nodes().containsKey(start)) {
            return false;
        }
        Session session = new Session();
        session.id = nextId++;
        session.dialogue = dialogue;
        session.npc = npc == null ? null : npc.getUUID();
        session.freeze = binding == null || binding.freeze();
        session.maxDistance = binding == null ? 8 : binding.maxDistance();
        session.content = ctx.content();
        SESSIONS.put(player.getUUID(), session);
        enter(player, session, start);
        return true;
    }

    /** The NPC of the player's current session, so a follow-up dialogue keeps talking to it. */
    public static Entity npc(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.npc == null ? null : player.serverLevel().getEntity(session.npc);
    }

    private static boolean current(ServerPlayer player, Session session) {
        return SESSIONS.get(player.getUUID()) == session;
    }

    private static boolean cooling(PlayerQuestData data, String key, long now) {
        return data.cooldowns.getOrDefault(key, 0L) > now;
    }

    private static void enter(ServerPlayer player, Session session, String nodeId) {
        Node node = session.dialogue.nodes().get(nodeId);
        ServerCtx ctx = QuestEngine.ctx(player);
        long now = ctx.gameTime();
        String key = session.dialogue.id() + "#" + nodeId;
        if (node == null || cooling(ctx.data(), key, now)) {
            close(player);
            return;
        }
        if (node.cooldownSeconds() > 0) {
            ctx.data().cooldowns.put(key, now + node.cooldownSeconds() * 20L);
            ctx.changed();
        }
        session.node = node;
        session.sequence++;
        session.offered.clear();

        Entity npc = npc(player);
        QuestEngine.run(ctx, node.actions());
        if (!current(player, session)) {
            return; // an action closed this dialogue or opened another
        }
        QuestEngine.objective(player, "solace:dialogue_node",
                objective -> session.dialogue.id().equals(objective.ref()) && nodeId.equals(objective.name()), 1, npc);
        if (!current(player, session)) {
            return;
        }

        ctx = QuestEngine.ctx(player);
        ctx.entity = npc;
        Component text = Component.empty();
        for (Dialogue.Variant variant : node.text()) {
            if (variant.when() == null || variant.when().test(ctx)) {
                text = variant.text();
                break;
            }
        }
        List<Views.ChoiceView> choices = new ArrayList<>();
        for (int i = 0; i < node.choices().size(); i++) {
            Choice choice = node.choices().get(i);
            if (choice.visibleWhen() != null && !choice.visibleWhen().test(ctx) || cooling(ctx.data(), key + "/" + i, now)) {
                continue;
            }
            boolean enabled = choice.enabledWhen() == null || choice.enabledWhen().test(ctx);
            session.offered.add(i);
            choices.add(new Views.ChoiceView(i, enabled || choice.disabledText() == null ? choice.text()
                    : choice.disabledText(), enabled));
        }
        session.autoAt = node.autoAdvanceSeconds() > 0 && node.to() != null ? now + node.autoAdvanceSeconds() * 20L : -1;

        Component speaker = node.speaker() != null ? node.speaker() : npc != null ? npc.getName() : Component.empty();
        // Journal: keep the last 100 lines the player has read.
        List<String> log = ctx.data().log;
        log.add(Component.Serializer.toJson(Component.empty().append(speaker.copy().withStyle(ChatFormatting.GOLD))
                .append(": ").append(text)));
        if (log.size() > 100) {
            log.remove(0);
        }
        ctx.changed();

        QuestNetwork.dialogueNode(player, session.id, session.sequence, npc != null && node.entityPortrait()
                        ? npc.getId() : -1, node.itemPortrait() == null ? "" : node.itemPortrait().toString(), speaker,
                text, choices, node.to() != null, node.autoAdvanceSeconds() * 20);
    }

    private static Session checked(ServerPlayer player, int sessionId, int sequence) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id != sessionId || session.sequence != sequence) {
            return null; // stale or forged
        }
        if (!inReach(player, session)) {
            close(player);
            return null;
        }
        return session;
    }

    private static boolean inReach(ServerPlayer player, Session session) {
        if (!player.isAlive() || session.content != QuestContent.current) {
            return false;
        }
        if (session.npc == null) {
            return true;
        }
        Entity npc = player.serverLevel().getEntity(session.npc);
        return npc != null && npc.isAlive()
                && npc.distanceToSqr(player) <= (double) session.maxDistance * session.maxDistance;
    }

    /** Client intent "I pick choice {@code index}". */
    public static void choose(ServerPlayer player, int sessionId, int sequence, int index) {
        Session session = checked(player, sessionId, sequence);
        if (session == null || !session.offered.contains(index)) {
            return;
        }
        Node node = session.node;
        Choice choice = node.choices().get(index);
        ServerCtx ctx = QuestEngine.ctx(player);
        ctx.entity = npc(player);
        String key = session.dialogue.id() + "#" + node.id() + "/" + index;
        // State may have changed since the node was shown: check again.
        if (choice.visibleWhen() != null && !choice.visibleWhen().test(ctx)
                || choice.enabledWhen() != null && !choice.enabledWhen().test(ctx)
                || cooling(ctx.data(), key, ctx.gameTime())) {
            enter(player, session, node.id());
            return;
        }
        for (Reward action : choice.actions()) {
            // A choice that takes items is refused as a whole when the player does not have them.
            if (action.type().equals("solace:take_item") && !action.on() && player.getInventory()
                    .clearOrCountMatchingItems(stack -> action.target().equals(ForgeRegistries.ITEMS.getKey(stack.getItem())),
                            0, player.inventoryMenu.getCraftSlots()) < action.count()) {
                player.displayClientMessage(Component.literal("You do not have what this requires.")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        if (choice.cooldownSeconds() > 0) {
            ctx.data().cooldowns.put(key, ctx.gameTime() + choice.cooldownSeconds() * 20L);
            ctx.changed();
        }
        QuestEngine.post(QuestEvent.Type.DIALOGUE_CHOICE, player, session.dialogue.id(), node.id(), index);
        QuestEngine.run(ctx, choice.actions());
        QuestLogic.evaluate(QuestEngine.ctx(player), QuestEngine.events(player));
        if (current(player, session)) {
            enter(player, session, choice.to());
        }
    }

    /** Client intent "continue" on a node without choices. */
    public static void advance(ServerPlayer player, int sessionId, int sequence) {
        Session session = checked(player, sessionId, sequence);
        if (session != null && session.node.choices().isEmpty() && session.node.to() != null) {
            enter(player, session, session.node.to());
        }
    }

    public static void close(ServerPlayer player, int sessionId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.id == sessionId) {
            close(player);
        }
    }

    public static void close(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            QuestNetwork.dialogueClose(player, session.id);
        }
    }

    public static void forget(UUID player) {
        SESSIONS.remove(player);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /**
     * Every server tick, over open sessions only: end sessions whose player left, died, walked
     * away or whose content was reloaded; hold the NPC still and facing the player; auto-advance.
     * The NPC is "frozen" only by this loop, so it is released the moment the session is gone.
     */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, Session> entry : List.copyOf(SESSIONS.entrySet())) {
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SESSIONS.remove(entry.getKey());
                continue;
            }
            if (!inReach(player, session)) {
                close(player);
                continue;
            }
            if (session.freeze && session.npc != null
                    && player.serverLevel().getEntity(session.npc) instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
            if (session.autoAt >= 0 && now >= session.autoAt) {
                enter(player, session, session.node.to());
            }
        }
    }
}
