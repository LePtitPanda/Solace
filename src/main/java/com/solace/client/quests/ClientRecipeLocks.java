package com.solace.client.quests;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.Set;

/** Recipes the server says are locked for this player; JEI and EMI hide them. */
public final class ClientRecipeLocks {
    public static volatile Set<ResourceLocation> locked = Set.of();
    /** Items made by locked recipes: EMI hides every recipe that makes one (machine copies too). */
    public static volatile Set<ResourceLocation> items = Set.of();

    private ClientRecipeLocks() {
    }

    public static void set(List<ResourceLocation> recipes, List<ResourceLocation> lockedItems) {
        Set<ResourceLocation> next = Set.copyOf(recipes);
        Set<ResourceLocation> before = locked;
        Set<ResourceLocation> nextItems = Set.copyOf(lockedItems);
        if (next.equals(before) && nextItems.equals(items)) {
            return;
        }
        locked = next;
        items = nextItems;
        if (ModList.get().isLoaded("jei")) {
            com.solace.compat.jei.SolaceJeiPlugin.applyRecipeLocks(before, next);
        }
        if (ModList.get().isLoaded("emi") && dev.emi.emi.runtime.EmiReloadManager.isLoaded()) {
            // EMI filters recipes once per reload (SolaceEmiPlugin); a lock change is rare.
            dev.emi.emi.runtime.EmiReloadManager.reload();
        }
    }
}
