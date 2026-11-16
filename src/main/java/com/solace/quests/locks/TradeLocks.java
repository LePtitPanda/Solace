package com.solace.quests.locks;

import com.solace.quests.content.Locks.Offer;
import com.solace.quests.content.Locks.TradeLock;
import com.solace.quests.content.QuestContent;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.ServerCtx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player trade locks. A locked offer is sent to the client out of stock ("disable") or left out
 * ("hide"); the server refuses to fill the result slot for it either way. Hidden offers shift the
 * client's indices, so the indices it sends back are mapped to the merchant's real list.
 */
public final class TradeLocks {
    /** Player -> client index -> server index, for the merchant list last sent with hidden offers. */
    private static final Map<UUID, int[]> MAPPING = new HashMap<>();

    private TradeLocks() {
    }

    /** 0 = open, 1 = disabled, 2 = hidden. */
    private static int state(ServerPlayer player, Merchant merchant, MerchantOffer offer) {
        QuestContent content = QuestContent.current;
        if (content.tradeLocks.isEmpty() || !(merchant instanceof Entity entity)) {
            return 0;
        }
        ServerCtx ctx = QuestEngine.ctx(player);
        ctx.entity = entity;
        int state = 0;
        for (TradeLock lock : content.tradeLocks.values()) {
            if (!lock.entity().matches(entity) || lock.when() != null && !lock.when().test(ctx)
                    || !lock.all() && lock.offers().stream().noneMatch(match -> matches(match, offer))
                    || lock.unlockWhen().test(ctx)) {
                continue;
            }
            state = Math.max(state, lock.hide() || lock.refuse() ? 2 : 1);
        }
        return state;
    }

    /**
     * A "refuse" lock that applies to this merchant for this player: the merchant does not trade at all (its offers
     * are ignored). Returns the lock, or null when the merchant trades as usual.
     */
    public static TradeLock refusal(ServerPlayer player, Entity merchant) {
        QuestContent content = QuestContent.current;
        if (content.tradeLocks.isEmpty() || !(merchant instanceof Merchant)) {
            return null;
        }
        ServerCtx ctx = QuestEngine.ctx(player);
        ctx.entity = merchant;
        for (TradeLock lock : content.tradeLocks.values()) {
            if (lock.refuse() && lock.entity().matches(merchant) && (lock.when() == null || lock.when().test(ctx))
                    && !lock.unlockWhen().test(ctx)) {
                return lock;
            }
        }
        return null;
    }

    private static boolean matches(Offer match, MerchantOffer offer) {
        return (match.result() == null || match.result().matches(offer.getResult()))
                && (match.cost() == null || match.cost().matches(offer.getBaseCostA())
                || match.cost().matches(offer.getCostB()));
    }

    public static boolean locked(ServerPlayer player, Merchant merchant, MerchantOffer offer) {
        return offer != null && state(player, merchant, offer) > 0;
    }

    /** The list this player is shown. Remembers how its indices map back. */
    public static MerchantOffers view(ServerPlayer player, Merchant merchant, MerchantOffers offers) {
        if (QuestContent.current.tradeLocks.isEmpty()) {
            MAPPING.remove(player.getUUID());
            return offers;
        }
        MerchantOffers shown = new MerchantOffers();
        int[] mapping = new int[offers.size()];
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            int state = state(player, merchant, offer);
            if (state == 2) {
                continue;
            }
            MerchantOffer copy = new MerchantOffer(offer.createTag());
            if (state == 1) {
                copy.setToOutOfStock();
            }
            mapping[shown.size()] = i;
            shown.add(copy);
        }
        MAPPING.put(player.getUUID(), java.util.Arrays.copyOf(mapping, shown.size()));
        return shown;
    }

    /** A trade index the client sent, in the merchant's real list. */
    public static int serverIndex(ServerPlayer player, int clientIndex) {
        int[] mapping = MAPPING.get(player.getUUID());
        return mapping == null || clientIndex < 0 || clientIndex >= mapping.length ? clientIndex : mapping[clientIndex];
    }

    public static void forget(UUID player) {
        MAPPING.remove(player);
    }
}
