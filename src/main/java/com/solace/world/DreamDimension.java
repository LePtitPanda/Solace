package com.solace.world;

import com.solace.Solace;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The one shared dream dimension (solace:dream). Survival players there can break blocks for no drops,
 * cannot break the floor, cannot place blocks or use buckets, and get Xaero's No Minimap effect.
 * Creative players are exempt, like bedrock.
 */
public final class DreamDimension {
    public static final ResourceLocation ID = new ResourceLocation(Solace.MOD_ID, "dream");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Solace.MOD_ID);
    public static final RegistryObject<Block> FLOOR = BLOCKS.register("dream_floor", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable()
                    .isValidSpawn((state, level, pos, type) -> false).pushReaction(PushReaction.BLOCK)));

    private static final ResourceLocation NO_MINIMAP = new ResourceLocation("xaerominimap", "no_minimap");

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    public static boolean isDream(Level level) {
        return level.dimension() == LEVEL;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || !isDream(level) || event.getPlayer().isCreative()) return;
        event.setCanceled(true);
        if (event.getState().is(FLOOR.get())) return;
        if (level.getBlockEntity(event.getPos()) instanceof Container container) container.clearContent();
        level.destroyBlock(event.getPos(), false);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !player.isCreative()
                && event.getLevel() instanceof Level level && isDream(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBucket(FillBucketEvent event) {
        if (!event.getEntity().isCreative() && isDream(event.getLevel())) event.setCanceled(true);
    }

    // /kill and the void still work: they bypass invulnerability.
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && isDream(player.level())
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dream").requires(source -> source.hasPermission(2))
                .executes(context -> send(context.getSource(), List.of(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> send(context.getSource(), EntityArgument.getPlayers(context, "targets")))));
    }

    private static int send(CommandSourceStack source, Collection<ServerPlayer> players) {
        ServerLevel dream = source.getServer().getLevel(LEVEL);
        if (dream == null) {
            source.sendFailure(Component.literal("The dream dimension is not loaded."));
            return 0;
        }
        for (ServerPlayer player : players) player.teleportTo(dream, 0.5, 1, 0.5, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Sent " + players.size() + " player(s) to the dream."), true);
        return players.size();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount % 5 != 0) return;
        MobEffect noMinimap = ForgeRegistries.MOB_EFFECTS.getValue(NO_MINIMAP);
        if (noMinimap == null) return;
        MobEffectInstance current = player.getEffect(noMinimap);
        if (isDream(player.level())) {
            if (current == null) {
                player.addEffect(new MobEffectInstance(noMinimap, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            }
        } else if (current != null && current.isInfiniteDuration()) {
            player.removeEffect(noMinimap);
        }
    }

    private DreamDimension() {
    }
}
