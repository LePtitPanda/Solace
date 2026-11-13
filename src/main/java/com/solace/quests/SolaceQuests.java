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

import com.solace.quests.content.QuestContent;
import com.solace.quests.npc.NpcRegistry;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Solace Quests wiring: network, reload listener, commands and the game events that drive objectives. */
public final class SolaceQuests {
    /** Babies just bred (not hatched). Cleared when the child joins or leaves. */
    private static final Set<UUID> BRED = ConcurrentHashMap.newKeySet();
    /** Animals already counted as tamed, so the event and the ownership check do not count twice. */
    private static final Set<UUID> TAMED = ConcurrentHashMap.newKeySet();
    /** Right-clicked ownable animal -> player, checked once on the next tick. */
    private static final Map<UUID, UUID> WATCHED = new HashMap<>();

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
                    && (objective.name() == null || victim.getName().getString().contains(objective.name())), 1, victim,
                    mode -> entityKey(mode, victim));
        }
    }

    /** The key a "distinct" objective counts for this entity. */
    private static String entityKey(String mode, Entity entity) {
        return switch (mode) {
            case "entity_type" -> String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
            case "entity" -> entity.getStringUUID();
            default -> null;
        };
    }

    private static String itemKey(String mode, ItemStack stack) {
        return mode.equals("item") ? String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())) : null;
    }

    private static String blockKey(String mode, BlockState state) {
        return mode.equals("block") ? String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())) : null;
    }

    /** Entity objectives with no extra filter: tame, breed, shear, hatch. */
    private static void animal(ServerPlayer player, String type, Entity entity) {
        QuestEngine.objective(player, type, objective -> objective.target().matches(entity), 1, entity,
                mode -> entityKey(mode, entity));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTame(AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer player && real(player)) {
            TAMED.add(event.getAnimal().getUUID());
            animal(player, "solace:tame", event.getAnimal());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() instanceof ServerPlayer player && real(player) && event.getChild() != null) {
            BRED.add(event.getChild().getUUID());
            animal(player, "solace:breed", event.getChild());
        }
    }

    /**
     * A baby appears near a player without being bred: it hatched (an egg block, a thrown egg, a mod's
     * egg). Counted for players within 16 blocks who have a hatch objective for it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide()
                || !QuestContent.current.objectiveTypes.contains("solace:hatch")
                || !(event.getEntity() instanceof AgeableMob baby) || !baby.isBaby()) {
            return;
        }
        // Breeding fires BabyEntitySpawnEvent before the child joins the level.
        if (BRED.remove(baby.getUUID())) {
            return;
        }
        for (Player nearby : event.getLevel().getEntitiesOfClass(Player.class, baby.getBoundingBox().inflate(16))) {
            if (nearby instanceof ServerPlayer player && real(player)) {
                animal(player, "solace:hatch", baby);
            }
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (!event.getLevel().isClientSide() && reason != null && reason.shouldDestroy()
                && entity.getServer() != null && NpcRegistry.identity(entity) != null) {
            NpcRegistry.get(entity.getServer()).removed(entity);
        }
        BRED.remove(entity.getUUID());
    }

    /**
     * A crafting grid that keeps its items (Visual Workbench) reopens without recomputing its result, so
     * Polymorph never gets asked and its button is missing. Recompute once on open.
     */
    @SubscribeEvent
    public static void onMenuOpen(net.minecraftforge.event.entity.player.PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof net.minecraft.world.inventory.CraftingMenu) {
            net.minecraft.world.inventory.CraftingContainer grid =
                    ((com.solace.mixin.CraftingMenuAccessor) event.getContainer()).solace$getCraftSlots();
            if (!grid.isEmpty()) {
                event.getContainer().slotsChanged(grid);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onConvert(LivingConversionEvent.Post event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity().getServer() != null) {
            NpcRegistry.get(event.getEntity().getServer()).converted(event.getEntity(), event.getOutcome());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && real(player)) {
            QuestEngine.objective(player, "solace:break_block",
                    objective -> objective.target().matches(event.getState()), 1, null,
                    mode -> blockKey(mode, event.getState()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)) {
            QuestEngine.objective(player, "solace:place_block",
                    objective -> objective.target().matches(event.getPlacedBlock()), 1, null,
                    mode -> blockKey(mode, event.getPlacedBlock()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)
                && event.getHand() == InteractionHand.MAIN_HAND) {
            BlockState state = event.getLevel().getBlockState(event.getPos());
            QuestEngine.objective(player, "solace:interact_block", objective -> objective.target().matches(state), 1,
                    null, mode -> blockKey(mode, state));
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
                1, event.getTarget(), mode -> entityKey(mode, event.getTarget()));
        shear(player, event.getTarget());
        watchTaming(player, event.getTarget());
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
            QuestEngine.objective(player, type, objective -> objective.target().matches(stack), stack.getCount(), null,
                    mode -> itemKey(mode, stack));
        }
    }

    /** Right-click with shears on something that can be sheared right now: it is about to be. */
    private static void shear(ServerPlayer player, Entity target) {
        ItemStack held = player.getMainHandItem();
        if (QuestContent.current.objectiveTypes.contains("solace:shear") && held.is(Tags.Items.SHEARS)
                && target instanceof IForgeShearable shearable
                && shearable.isShearable(held, target.level(), target.blockPosition())) {
            animal(player, "solace:shear", target);
        }
    }

    /**
     * Mods often tame without AnimalTameEvent. Remember right-clicked ownable animals that are not the
     * player's yet; if one belongs to the player a tick later, it was tamed.
     */
    private static void watchTaming(ServerPlayer player, Entity target) {
        if (QuestContent.current.objectiveTypes.contains("solace:tame") && target instanceof OwnableEntity ownable
                && !player.getUUID().equals(ownable.getOwnerUUID())) {
            WATCHED.put(target.getUUID(), player.getUUID());
        }
    }

    /** Items with a use time (food, potions, bows...) count when the use finishes. */
    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)) {
            ItemStack stack = event.getItem();
            QuestEngine.objective(player, "solace:use_item", objective -> objective.target().matches(stack), 1, null,
                    mode -> itemKey(mode, stack));
        }
    }

    /** Instant-use items count on right-click; the ones with a use time are handled above. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && real(player)
                && com.solace.quests.locks.RecipeLocks.learn(player, event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && real(player)
                && event.getItemStack().getUseDuration() == 0) {
            ItemStack stack = event.getItemStack();
            QuestEngine.objective(player, "solace:use_item", objective -> objective.target().matches(stack), 1, null,
                    mode -> itemKey(mode, stack));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        DialogueManager.tick(event.getServer());
        if (TAMED.size() > 10_000) {
            TAMED.clear(); // only guards against double counts within a tick or two
        }
        if (!WATCHED.isEmpty()) {
            WATCHED.forEach((animal, owner) -> {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(owner);
                Entity entity = player == null ? null : player.serverLevel().getEntity(animal);
                if (entity instanceof OwnableEntity ownable && owner.equals(ownable.getOwnerUUID())
                        && TAMED.add(animal)) {
                    animal(player, "solace:tame", entity);
                }
            });
            WATCHED.clear();
        }
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
        com.solace.quests.locks.RecipeLocks.forget(id);
        com.solace.quests.locks.TradeLocks.forget(id);
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
