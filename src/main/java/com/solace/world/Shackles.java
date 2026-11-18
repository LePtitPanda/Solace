package com.solace.world;

import com.solace.registry.SolaceEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Chained hands: no fighting, digging, building or using items. Doors, chests and talking still work. Both sides. */
public final class Shackles {
    private Shackles() {
    }

    public static boolean on(Player player) {
        return player != null && player.hasEffect(SolaceEffects.SHACKLED.get());
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (on(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (on(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (on(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && on(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        // Reading still works in cuffs (the old man's journal is read in the cell).
        if (on(event.getEntity()) && !event.getItemStack().is(net.minecraft.world.item.Items.WRITTEN_BOOK)
                && !event.getItemStack().is(net.minecraft.world.item.Items.WRITABLE_BOOK)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (on(event.getEntity())) event.setUseItem(Event.Result.DENY);
    }
}
