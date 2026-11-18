package com.solace.quests.state;

import com.solace.quests.api.QuestEvent;
import com.solace.quests.content.Faction;
import com.solace.quests.network.QuestNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reputation, stored per owner. A "villager gossip" faction is one world-wide standing: it moves
 * with first-hand vanilla gossip (see {@link #onGossip}) and is never re-read from nearby villagers.
 */
public final class Factions {
    /** Set while Solace writes gossip itself, which {@link #add} already counted. */
    private static boolean writing;
    private static int gossipTick = -1;
    /** This tick's gossip change per player, per villager's gossip container. */
    private static final Map<UUID, Map<Object, Integer>> gossipThisTick = new HashMap<>();
    /** The part of this tick's change already applied per player. */
    private static final Map<UUID, Integer> appliedThisTick = new HashMap<>();

    private Factions() {
    }

    public static int get(ServerCtx ctx, ResourceLocation id) {
        Faction faction = ctx.content().factions.get(id);
        return faction == null ? 0 : ctx.data().reputation.getOrDefault(id, faction.def());
    }

    public static void add(ServerCtx ctx, Faction faction, int amount, boolean propagate) {
        if (amount == 0) {
            return;
        }
        if (faction.gossip()) {
            // Real gossip too, so nearby trade prices react.
            writeGossip(ctx.player, faction, amount);
        }
        set(ctx, faction, get(ctx, faction.id()) + amount);
        if (propagate) {
            // One step only: relations of the related faction are not followed.
            for (Faction.Relation relation : faction.relations()) {
                Faction other = ctx.content().factions.get(relation.faction());
                if (other != null) {
                    add(ctx, other, (int) Math.round(amount * relation.factor()), false);
                }
            }
        }
    }

    public static void set(ServerCtx ctx, Faction faction, int value) {
        int old = get(ctx, faction.id());
        int now = Mth.clamp(value, faction.min(), faction.max());
        if (old == now) {
            return;
        }
        ctx.data().reputation.put(faction.id(), now);
        ctx.changed();
        if (now < old) {
            ctx.player.displayClientMessage(Component.literal((now - old) + " reputation with ").append(faction.name())
                    .withStyle(ChatFormatting.RED), true);
        }
        Faction.Rank rank = faction.ranks().get(faction.rankIndex(now));
        if (faction.rankIndex(old) != faction.rankIndex(now)) {
            QuestNetwork.toast(ctx.player, "reputation", faction.name(), rank.name(), faction.icon());
        }
        MinecraftForge.EVENT_BUS.post(new QuestEvent(QuestEvent.Type.REPUTATION_CHANGED, ctx.player, faction.id(),
                rank.id(), now));
    }

    /**
     * Quest rewards and penalties on a gossip faction become real vanilla gossip on the villagers
     * around the player, so trade prices react as if the player had earned it.
     */
    private static void writeGossip(ServerPlayer player, Faction faction, int amount) {
        writing = true;
        try {
            writeGossipNearby(player, faction, amount);
        } finally {
            writing = false;
        }
    }

    private static void writeGossipNearby(ServerPlayer player, Faction faction, int amount) {
        for (Villager villager : nearby(player, faction)) {
            if (amount > 0) {
                // Vanilla weights: MAJOR_POSITIVE counts 5 per point, MINOR_POSITIVE 1.
                if (amount >= 5) {
                    villager.getGossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, amount / 5);
                }
                if (amount % 5 > 0) {
                    villager.getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, amount % 5);
                }
            } else {
                villager.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, -amount);
            }
        }
    }

    private static List<Villager> nearby(ServerPlayer player, Faction faction) {
        return player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(faction.radius()),
                LivingEntity::isAlive);
    }

    /**
     * A first-hand gossip change on one villager. One deed is often told to every witness in the
     * same tick (a killed villager, a crime, a raid won), so per tick only the strongest
     * villager's change counts, not the sum over villagers.
     */
    public static void onGossip(Object container, UUID id, int delta) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (writing || server == null || !server.isSameThread()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            return;
        }
        ServerCtx ctx = QuestEngine.ctx(player);
        if (ctx.content().factions.values().stream().noneMatch(Faction::gossip)) {
            return;
        }
        if (server.getTickCount() != gossipTick) {
            gossipTick = server.getTickCount();
            gossipThisTick.clear();
            appliedThisTick.clear();
        }
        Map<Object, Integer> perVillager = gossipThisTick.computeIfAbsent(id, k -> new IdentityHashMap<>());
        perVillager.merge(container, delta, Integer::sum);
        int strongest = 0;
        for (int value : perVillager.values()) {
            if (Math.abs(value) > Math.abs(strongest)) {
                strongest = value;
            }
        }
        int step = strongest - appliedThisTick.getOrDefault(id, 0);
        appliedThisTick.put(id, strongest);
        if (step == 0) {
            return;
        }
        for (Faction faction : ctx.content().factions.values()) {
            if (faction.gossip()) {
                set(ctx, faction, get(ctx, faction.id()) + step);
            }
        }
    }
}
