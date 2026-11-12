package com.solace.quests.state;

import com.solace.quests.api.QuestEvent;
import com.solace.quests.content.Faction;
import com.solace.quests.network.QuestNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;

/** Reputation. Stored per owner, except "villager gossip" factions which mirror vanilla gossip. */
public final class Factions {
    private Factions() {
    }

    /** For a gossip faction this is the value last seen near villagers (refreshed by {@link #refreshGossip}). */
    public static int get(ServerCtx ctx, ResourceLocation id) {
        Faction faction = ctx.content().factions.get(id);
        return faction == null ? 0 : ctx.data().reputation.getOrDefault(id, faction.def());
    }

    public static void add(ServerCtx ctx, Faction faction, int amount, boolean propagate) {
        if (amount == 0) {
            return;
        }
        if (faction.gossip()) {
            writeGossip(ctx.player, faction, amount);
            refreshGossip(ctx);
        } else {
            set(ctx, faction, get(ctx, faction.id()) + amount);
        }
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
        Faction.Rank rank = faction.ranks().get(faction.rankIndex(now));
        if (faction.rankIndex(old) != faction.rankIndex(now)) {
            QuestNetwork.toast(ctx.player, "reputation", faction.name(), rank.name(), faction.icon());
        }
        MinecraftForge.EVENT_BUS.post(new QuestEvent(QuestEvent.Type.REPUTATION_CHANGED, ctx.player, faction.id(),
                rank.id(), now));
    }

    /**
     * Quest rewards and penalties on a gossip faction become real vanilla gossip on the villagers
     * around the player, so trade prices and the Thief mod react as if the player had earned it.
     */
    private static void writeGossip(ServerPlayer player, Faction faction, int amount) {
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

    /** Re-reads vanilla gossip for every gossip faction; keeps the last value when no villager is near. */
    public static void refreshGossip(ServerCtx ctx) {
        for (Faction faction : ctx.content().factions.values()) {
            if (!faction.gossip()) {
                continue;
            }
            List<Villager> villagers = nearby(ctx.player, faction);
            if (villagers.isEmpty()) {
                continue;
            }
            int sum = 0;
            for (Villager villager : villagers) {
                sum += villager.getPlayerReputation(ctx.player);
            }
            set(ctx, faction, Math.round((float) sum / villagers.size()));
        }
    }
}
