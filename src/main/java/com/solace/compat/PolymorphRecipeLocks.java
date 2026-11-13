package com.solace.compat;

import com.illusivesoulworks.polymorph.api.PolymorphApi;
import com.illusivesoulworks.polymorph.api.common.base.IRecipePair;
import com.solace.quests.locks.RecipeLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Recipe locks for Polymorph, through its API (its classes load too early to be mixed into): after a
 * crafting grid is computed, locked recipes are removed from the player's Polymorph list and it is re-sent.
 */
public final class PolymorphRecipeLocks {
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("polymorph");

    private PolymorphRecipeLocks() {
    }

    public static void filter(ServerPlayer player) {
        if (LOADED) {
            Api.filter(player);
        }
    }

    /** Only touched when Polymorph is installed. */
    private static final class Api {
        static void filter(ServerPlayer player) {
            PolymorphApi.common().getRecipeData(player).ifPresent(data -> {
                SortedSet<IRecipePair> list = data.getRecipesList();
                if (list == null || list.stream().noneMatch(pair -> RecipeLocks.locked(player, pair.getResourceLocation()))) {
                    return;
                }
                SortedSet<IRecipePair> shown = new TreeSet<>(list);
                shown.removeIf(pair -> RecipeLocks.locked(player, pair.getResourceLocation()));
                data.setRecipesList(shown);
                PolymorphApi.common().getPacketDistributor().sendRecipesListS2C(player, shown);
            });
        }
    }
}
