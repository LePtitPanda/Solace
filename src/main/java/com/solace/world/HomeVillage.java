package com.solace.world;

import com.solace.Solace;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The player's home village: the nearest village to world spawn, found once at world load and kept in
 * Solace world data. A player joining for the first time is placed on the sleeping bag in the old prison
 * under that village's centre, which also becomes his respawn point.
 */
public final class HomeVillage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = Solace.MOD_ID + "_world_data";
    private static final int SEARCH_RADIUS_CHUNKS = 128;
    // Version 2 invalidates locations produced by the old experimental predictor.
    private static final int LOCATOR_VERSION = 2;
    // shortcut: the Astralis prison is hard-coded; make these config values if another pack needs a different start.
    private static final ResourceLocation PRISON_CENTRE =
            new ResourceLocation("minecraft", "village/plains/town_centers/plains_meeting_point_2");
    private static final ResourceLocation SLEEPING_BAG = new ResourceLocation("comforts", "sleeping_bag_gray");

    private HomeVillage() {
    }

    /** At overworld load: find the home village once, then keep other structures away from it. */
    public static void onOverworldLoad(ServerLevel level) {
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;
        SolaceWorldData data = data(level);
        BlockPos village = data.homeVillage(LOCATOR_VERSION);
        if (village == null) {
            LOGGER.info("[Solace] Finding the home village during world load.");
            long started = System.currentTimeMillis();
            village = level.findNearestMapStructure(StructureTags.VILLAGE, level.getSharedSpawnPos(), SEARCH_RADIUS_CHUNKS, false);
            LOGGER.info("[Solace] Home village search took {} ms.", System.currentTimeMillis() - started);
            if (village == null) {
                LOGGER.warn("[Solace] No village within {} placement rings of spawn; there is no home village.", SEARCH_RADIUS_CHUNKS);
                IntroVillageStructureProtection.clearTarget(level);
                return;
            }
            LOGGER.info("[Solace] Home village at {} {} {}.", village.getX(), village.getY(), village.getZ());
            data.setHomeVillage(village, LOCATOR_VERSION);
        }
        IntroVillageStructureProtection.setTarget(level, village);
        // World spawn goes to the prison, so the start region is prepared around the village and a new player
        // is never placed far from it (the village can be kilometres from the original spawn).
        BlockPos bag = prisonSleepingBag(level);
        if (bag != null && !bag.equals(level.getSharedSpawnPos())) {
            LOGGER.info("[Solace] World spawn moved to the prison sleeping bag at {} {} {}.", bag.getX(), bag.getY(), bag.getZ());
            level.setDefaultSpawnPos(bag, 0.0F);
        }
    }

    /** The home village found at world load, or null if none was found. Never searches. */
    public static BlockPos homeVillage(ServerLevel overworld) {
        return data(overworld).homeVillage(LOCATOR_VERSION);
    }

    /** First join only: put the player on the prison sleeping bag and make it his respawn point. */
    public static void placeNewPlayer(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        SolaceWorldData data = data(overworld);
        if (data.isPlaced(player.getUUID())) {
            // Reconnected before the opening was ready: hold him again so the ready signal is sent.
            if (!com.solace.quests.api.QuestApi.hasFlag(player, "intro.ready")) IntroHold.hold(player);
            return;
        }
        BlockPos bag = prisonSleepingBag(overworld);
        if (bag == null) {
            LOGGER.warn("[Solace] No prison sleeping bag found in the home village; {} stays at world spawn.",
                    player.getGameProfile().getName());
            return; // retried at the next login
        }
        IntroHold.hold(player);
        player.teleportTo(overworld, bag.getX() + 0.5, bag.getY() + 0.6, bag.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.setRespawnPosition(ServerLevel.OVERWORLD, bag, player.getYRot(), true, false);
        data.setPlaced(player.getUUID());
    }

    private static BlockPos prisonSleepingBag(ServerLevel overworld) {
        BlockPos home = homeVillage(overworld);
        if (home == null || !ForgeRegistries.BLOCKS.containsKey(SLEEPING_BAG)) return null;
        BoundingBox centre = VillageBuildingLocator.find(overworld, home, PRISON_CENTRE);
        if (centre == null) return null;
        return VillageBuildingLocator.findBlock(overworld, centre,
                block -> SLEEPING_BAG.equals(ForgeRegistries.BLOCKS.getKey(block.getState().getBlock())));
    }

    private static SolaceWorldData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SolaceWorldData::load, SolaceWorldData::new, DATA_NAME);
    }
}
