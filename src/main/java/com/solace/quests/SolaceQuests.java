package com.solace.quests;

import com.solace.quests.compat.ThiefCompat;
import com.solace.quests.content.QuestContentLoader;
import com.solace.quests.dialogue.DialogueManager;
import com.solace.quests.editor.EditorServer;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.network.QuestSync;
import com.solace.quests.state.Markers;
import com.solace.quests.state.QuestEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

/** Solace Quests wiring: network, reload listener, commands and the game events that drive objectives. */
public final class SolaceQuests {
    private SolaceQuests() {
    }

    public static void init() {
        QuestNetwork.register();
        MinecraftForge.EVENT_BUS.register(SolaceQuests.class);
        if (ModList.get().isLoaded("thief")) {
            ThiefCompat.register();
        }
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestContentLoader());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
    }

    // LOWEST and not receiving cancelled events: only count what actually happened.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && real(player)) {
            LivingEntity victim = event.getEntity();
            QuestEngine.objective(player, "solace:kill", objective -> objective.target().matches(victim)
                    && (objective.name() == null || victim.getName().getString().contains(objective.name())), 1, victim);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && real(player)) {
            QuestEngine.objective(player, "solace:break_block",
                    objective -> objective.target().matches(event.getState()), 1, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)) {
            QuestEngine.objective(player, "solace:place_block",
                    objective -> objective.target().matches(event.getPlacedBlock()), 1, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)
                && event.getHand() == InteractionHand.MAIN_HAND) {
            BlockState state = event.getLevel().getBlockState(event.getPos());
            QuestEngine.objective(player, "solace:interact_block", objective -> objective.target().matches(state), 1,
                    null);
        }
    }

    /**
     * NPC dialogue. Only an entity that matches a binding has its vanilla interaction cancelled,
     * so ordinary villagers still trade. HIGH so the dialogue wins over other mods' handlers.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !real(player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            // The off-hand pass of a click that already opened a dialogue must not reach vanilla (trading).
            if (DialogueManager.npc(player) == event.getTarget()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        QuestEngine.objective(player, "solace:interact_entity", objective -> objective.target().matches(event.getTarget())
                && (objective.name() == null || event.getTarget().getName().getString().contains(objective.name())),
                1, event.getTarget());
        if (DialogueManager.interact(player, event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        item(event, "solace:craft", event.getCrafting());
    }

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        item(event, "solace:smelt", event.getSmelting());
    }

    private static void item(PlayerEvent event, String type, ItemStack stack) {
        if (event.getEntity() instanceof ServerPlayer player && real(player) && !stack.isEmpty()) {
            QuestEngine.objective(player, type, objective -> objective.target().matches(stack), stack.getCount(), null);
        }
    }

    /** Items with a use time (food, potions, bows...) count when the use finishes. */
    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)) {
            ItemStack stack = event.getItem();
            QuestEngine.objective(player, "solace:use_item", objective -> objective.target().matches(stack), 1, null);
        }
    }

    /** Instant-use items count on right-click; the ones with a use time are handled above. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)
                && event.getItemStack().getUseDuration() == 0) {
            ItemStack stack = event.getItemStack();
            QuestEngine.objective(player, "solace:use_item", objective -> objective.target().matches(stack), 1, null);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        DialogueManager.tick(event.getServer());
        // Each player is visited once a second, spread over the 20 ticks by entity id.
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if ((player.tickCount + player.getId()) % 20 == 0) {
                QuestEngine.tick(player);
            }
        }
        if (event.getServer().getTickCount() % 5 == 0) {
            QuestSync.flush(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestSync.dirty(player);
            QuestEngine.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        DialogueManager.forget(id);
        QuestSync.forget(id);
        QuestNetwork.forget(id);
        Markers.forget(id);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        EditorServer.snapshot(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DialogueManager.clear();
        EditorServer.stop();
    }

    private static boolean real(ServerPlayer player) {
        return !(player instanceof FakePlayer);
    }
}
