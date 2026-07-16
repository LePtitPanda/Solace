package com.solace.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.solace.compat.GulingSentinelProofSupport;
import com.solace.compat.IntegratedApiLocatorShutdownSupport;
import com.solace.compat.InstrumentCockroachBandSupport;
import com.solace.config.PerformanceConfig;
import com.solace.performance.ServerWorkScheduler;
import com.solace.performance.VMinusIngredientFilterCache;
import com.solace.world.BalloonIntroManager;
import com.solace.world.JigsawVillageBuildingTool;
import com.solace.world.SolaceIntroBalloonAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommonEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> DEATH_DISMOUNT_BYPASS = ConcurrentHashMap.newKeySet();
    private static final ResourceLocation MOON_ID = ResourceLocation.fromNamespaceAndPath("ad_astra", "moon");
    private static final ResourceLocation GULING_SENTINEL_ID = ResourceLocation.fromNamespaceAndPath("eeeabsmobs", "guling_sentinel");

    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerWorkScheduler.registerLevel(level);
            BalloonIntroManager.precomputeIntroVillage(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerWorkScheduler.unregisterLevel(level);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        ServerWorkScheduler.onServerTick(event);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerWorkScheduler.reset(event.getServer());
        IntegratedApiLocatorShutdownSupport.shutdownAfterWorldSave();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof SolaceIntroBalloonAccess access) || !access.solace$isIntroBalloon() || access.solace$hasLanded()) {
            return;
        }

        BalloonIntroManager.refreshIntroSafetySpawn(player, vehicle);
        boolean wasLanded = access.solace$hasLanded();
        access.solace$tickLandingMovementCheck();
        if (!wasLanded && access.solace$hasLanded()) {
            BalloonIntroManager.completeIntroLanding(player, vehicle);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InstrumentCockroachBandSupport.tryGiveInstrumentToMob(event);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !MOON_ID.equals(level.dimension().location())
                || !GULING_SENTINEL_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()))
                || !ModList.get().isLoaded("ad_astra_giselle_addon")) {
            return;
        }

        GulingSentinelProofSupport.applyGravityProof(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        InstrumentCockroachBandSupport.tick(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (!event.isDismounting()) {
            return;
        }

        Entity mounted = event.getEntityBeingMounted();
        Entity rider = event.getEntityMounting();
        if (rider instanceof ServerPlayer player && DEATH_DISMOUNT_BYPASS.contains(player.getUUID())) {
            return;
        }

        if (mounted instanceof SolaceIntroBalloonAccess access && access.solace$isIntroBalloon() && !access.solace$hasLanded()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof SolaceIntroBalloonAccess access) || !access.solace$isIntroBalloon() || access.solace$hasLanded()) {
            return;
        }

        DEATH_DISMOUNT_BYPASS.add(player.getUUID());
        try {
            player.stopRiding();
        } finally {
            DEATH_DISMOUNT_BYPASS.remove(player.getUUID());
        }
        vehicle.discard();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BalloonIntroManager.requestIntro(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("solace")
                .then(Commands.literal("stop_balloon")
                        .executes(context -> stopIntroBalloon(context.getSource())))
                .then(Commands.literal("what_building")
                        .executes(context -> whatBuilding(context.getSource())))
                .then(Commands.literal("village_buildings")
                        .executes(context -> listVillageBuildings(context.getSource())))
                .then(Commands.literal("performance")
                        .executes(context -> showPerformanceStatus(context.getSource()))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> reloadPerformanceConfig(context.getSource()))))
                .then(Commands.literal("village_jigsaws")
                        .then(Commands.literal("place_test")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> runVillageJigsawPlacementTest(context.getSource(), "command")))
                        .executes(context -> showVillageJigsaws(context.getSource()))));

        dispatcher.register(Commands.literal("what_building")
                .executes(context -> whatBuilding(context.getSource())));
        dispatcher.register(Commands.literal("village_buildings")
                .executes(context -> listVillageBuildings(context.getSource())));
    }

    private static int showPerformanceStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(ServerWorkScheduler.describeStatus(source.getServer())), false);
        if (ModList.get().isLoaded("vminus")) {
            source.sendSuccess(() -> Component.literal(VMinusIngredientFilterCache.describeStatus()), false);
        }
        return 1;
    }

    private static int reloadPerformanceConfig(CommandSourceStack source) {
        PerformanceConfig.reload();
        source.sendSuccess(() -> Component.literal("Reloaded Solace performance prototype config."), false);
        return showPerformanceStatus(source);
    }

    private static int stopIntroBalloon(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can stop an intro balloon."));
            return 0;
        }

        if (!player.isCreative()) {
            source.sendFailure(Component.literal("You must be in creative mode to stop the intro balloon."));
            return 0;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof SolaceIntroBalloonAccess access) || !access.solace$isIntroBalloon() || access.solace$hasLanded()) {
            source.sendFailure(Component.literal("You are not riding an active intro balloon."));
            return 0;
        }

        access.solace$markLanded();
        BalloonIntroManager.completeIntroLanding(player, vehicle);
        source.sendSuccess(() -> Component.literal("Stopped the intro balloon."), false);
        return 1;
    }

    private static int showVillageJigsaws(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Configured plains village building rules: " + JigsawVillageBuildingTool.describeUniqueTargets()), false);

        java.util.List<String> reports = JigsawVillageBuildingTool.describeRecentReports();
        if (reports.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No plains village jigsaw reports recorded yet."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Recent plains village jigsaw reports:"), false);
        for (String report : reports) {
            source.sendSuccess(() -> Component.literal(report), false);
        }

        return reports.size();
    }

    private static int runVillageJigsawPlacementTest(CommandSourceStack source, String reason) {
        Vec3 position = source.getPosition();
        CommandSourceStack testSource = source
                .withPermission(4)
                .withSuppressedOutput()
                .withPosition(position)
                .withRotation(Vec2.ZERO);
        String command = "place jigsaw minecraft:village/plains/town_centers minecraft:bottom 7 "
                + (int) Math.floor(position.x) + " "
                + (int) Math.floor(position.y) + " "
                + (int) Math.floor(position.z);
        int result = source.getServer().getCommands().performPrefixedCommand(testSource, command);
        java.util.List<String> reports = JigsawVillageBuildingTool.describeRecentReports();
        LOGGER.info("[Solace] Village jigsaw placement test ({}) ran `{}` result {}; reports: {}", reason, command, result, reports);

        source.sendSuccess(() -> Component.literal("Placed plains village jigsaw test; command result " + result + "."), false);
        if (reports.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No plains village jigsaw reports recorded yet."), false);
        } else {
            for (String report : reports) {
                source.sendSuccess(() -> Component.literal(report), false);
            }
        }
        return result;
    }

    private static int whatBuilding(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can query buildings."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        StructureManager structureManager = level.structureManager();
        java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, ?> structures = structureManager.getAllStructuresAt(pos);

        boolean foundPiece = false;
        for (net.minecraft.world.level.levelgen.structure.Structure structure : structures.keySet()) {
            ResourceLocation structureId = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(structure);
            if (structureId == null || !structureId.getPath().contains("village")) {
                continue;
            }

            net.minecraft.world.level.levelgen.structure.StructureStart start = structureManager.getStructureAt(pos, structure);
            if (start == null || !start.isValid()) {
                continue;
            }

            for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                if (piece.getBoundingBox().isInside(pos)) {
                    if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece poolPiece) {
                        java.util.Optional<ResourceLocation> templateId = JigsawVillageBuildingTool.templateId(poolPiece.getElement());
                        if (templateId.isPresent()) {
                            source.sendSuccess(() -> Component.literal("You are standing in village building: " + templateId.get().toString()), false);
                            foundPiece = true;
                            break;
                        }
                    }
                }
            }
            if (foundPiece) break;
        }

        if (!foundPiece) {
            source.sendFailure(Component.literal("You are not standing inside any village building piece."));
            return 0;
        }
        return 1;
    }

    private static int listVillageBuildings(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can list village buildings."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        StructureManager structureManager = level.structureManager();
        java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, ?> structures = structureManager.getAllStructuresAt(pos);

        net.minecraft.world.level.levelgen.structure.StructureStart villageStart = null;
        ResourceLocation villageId = null;
        for (net.minecraft.world.level.levelgen.structure.Structure structure : structures.keySet()) {
            ResourceLocation structureId = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(structure);
            if (structureId != null && structureId.getPath().contains("village")) {
                net.minecraft.world.level.levelgen.structure.StructureStart start = structureManager.getStructureAt(pos, structure);
                if (start != null && start.isValid()) {
                    villageStart = start;
                    villageId = structureId;
                    break;
                }
            }
        }

        if (villageStart == null || villageId == null) {
            source.sendFailure(Component.literal("You are not inside a village."));
            return 0;
        }

        java.util.Map<ResourceLocation, Integer> counts = new java.util.LinkedHashMap<>();
        for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : villageStart.getPieces()) {
            if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece poolPiece) {
                java.util.Optional<ResourceLocation> templateId = JigsawVillageBuildingTool.templateId(poolPiece.getElement());
                templateId.ifPresent(id -> {
                    String path = id.getPath();
                    if (path.contains("streets/") || path.contains("villagers/") || path.contains("common/") || path.contains("terminators/")) {
                        return;
                    }
                    counts.merge(id, 1, Integer::sum);
                });
            }
        }

        if (counts.isEmpty()) {
            source.sendFailure(Component.literal("No building pieces found in this village."));
            return 0;
        }

        final ResourceLocation finalVillageId = villageId;
        source.sendSuccess(() -> Component.literal("Buildings in village (" + finalVillageId.toString() + "):"), false);
        for (java.util.Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
            final ResourceLocation key = entry.getKey();
            final int count = entry.getValue();
            source.sendSuccess(() -> Component.literal("- " + key.toString() + " x" + count), false);
        }

        return counts.size();
    }
}
