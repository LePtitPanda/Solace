package com.solace.quests.content;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * {@code gossip}: one stored world-wide standing that also moves with first-hand vanilla villager gossip.
 * {@code defendersAttackAt}: a rank id (or null); village defenders attack players at this rank or below.
 */
public record Faction(ResourceLocation id, Component name, int color, String icon, int def, int min, int max,
                      List<Rank> ranks, List<Relation> relations, boolean gossip, int radius,
                      String defendersAttackAt) {
    /** Applies from {@code at} upward; ranks are sorted by {@code at}. */
    public record Rank(String id, Component name, int at) {
    }

    public record Relation(ResourceLocation faction, double factor) {
    }

    /** Index into {@link #ranks} for a standing value. */
    public int rankIndex(int value) {
        int index = 0;
        for (int i = 0; i < ranks.size(); i++) {
            if (value >= ranks.get(i).at()) {
                index = i;
            }
        }
        return index;
    }

    public int rankIndex(String rankId) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).id().equals(rankId)) {
                return i;
            }
        }
        return -1;
    }

    /** The lowest standing defenders leave alone: the start of the rank above {@code defendersAttackAt}. */
    public int defendersSafeFrom() {
        int index = rankIndex(defendersAttackAt);
        return index + 1 < ranks.size() ? ranks.get(index + 1).at() : max + 1;
    }
}
