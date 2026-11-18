package com.solace.client.quests;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** Items whose ponders the server says this player may open (knowledge books read). Everything else is closed. */
public final class ClientPonderUnlocks {
    private static volatile boolean all;
    private static volatile Set<ResourceLocation> items = Set.of();

    private ClientPonderUnlocks() {
    }

    public static void set(boolean everything, List<ResourceLocation> open) {
        all = everything;
        items = Set.copyOf(open);
    }

    public static boolean locked(ResourceLocation item) {
        return !all && !items.contains(item);
    }
}
