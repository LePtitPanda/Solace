package com.solace.spawn;

import com.mojang.logging.LogUtils;
import com.solace.world.NaturalSurfaceSpawnSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class OverworldSpawnManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation HAMSTER = id("spawn", "hamster");
    private static final TagKey<EntityType<?>> ICY_BREED_HORSES = TagKey.create(Registries.ENTITY_TYPE,
            id("icys-better-horses", "breed_horses"));
    private static final ResourceLocation FLUTTER = id("alexsmobs", "flutter");
    private static final ResourceLocation GRAZER = id("caverns_and_chasms", "grazer");
    private static final TagKey<Block> FLUTTER_SPAWNS = TagKey.create(Registries.BLOCK, id("alexsmobs", "flutter_spawns"));
    private static final TagKey<Block> BENTHIC_PASS_THROUGH = TagKey.create(Registries.BLOCK, id("astralis", "benthic_spawn_pass_through"));
    private static final Set<ResourceLocation> BENTHIC_MOBS = ids(
            "alexsmobs:lobster",
            "alexsmobs:mantis_shrimp",
            "alexsmobs:triops",
            "aquaculture:atlantic_halibut",
            "aquaculture:pacific_halibut",
            "collectorsreap:chieftain_crab",
            "collectorsreap:tiger_prawn",
            "spawn:angler_fish",
            "spawn:clam",
            "spawn:octopus",
            "spawn:seahorse",
            "spawn:spider_crab",
            "upgrade_aquatic:nautilus"
    );
    private static final Set<ResourceLocation> SHORELINE_MOBS = ids(
            "alexsmobs:seagull",
            "alexsmobs:seal",
            "iter_rpg:bloated",
            "minecraft:turtle",
            "spawn:coastal_crab",
            "traveloptics:aqua_grandmaster"
    );
    private static final int[][] SHORELINE_OFFSETS = {
            {0, 0, 0},
            {12, -1, 0}, {12, -2, 0}, {12, -3, 0},
            {-12, -1, 0}, {-12, -2, 0}, {-12, -3, 0},
            {0, -1, 12}, {0, -2, 12}, {0, -3, 12},
            {0, -1, -12}, {0, -2, -12}, {0, -3, -12},
            {9, -1, 9}, {9, -2, 9}, {9, -3, 9},
            {9, -1, -9}, {9, -2, -9}, {9, -3, -9},
            {-9, -1, 9}, {-9, -2, 9}, {-9, -3, 9},
            {-9, -1, -9}, {-9, -2, -9}, {-9, -3, -9}
    };

    private static final ThreadLocal<ServerLevel> ACTIVE_NATIVE_SPAWN_LEVEL = new ThreadLocal<>();
    private static final Map<StaticRuleKey, List<OverworldSpawnConfig.CompiledRule>> STATIC_RULE_CACHE = new HashMap<>();
    private static final Map<ServerLevel, CountSnapshot> ENTITY_COUNTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, CavePlayerSnapshot> CAVE_PLAYERS = new WeakHashMap<>();
    private static volatile OverworldSpawnConfig.RuntimeConfig config = OverworldSpawnConfig.RuntimeConfig.disabled();
    private static volatile boolean loaded;

    private OverworldSpawnManager() {
    }

    public static synchronized void reload() {
        OverworldSpawnConfig.RuntimeConfig loadedConfig = OverworldSpawnConfig.load();
        config = loadedConfig;
        loaded = true;
        STATIC_RULE_CACHE.clear();
        ENTITY_COUNTS.clear();
        CAVE_PLAYERS.clear();
    }

    public static String describeStatus() {
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        if (!current.enabled()) {
            return "Astralis native spawn migration is disabled because its config did not load.";
        }
        int compiledRules = current.rulesByCategory().values().stream().mapToInt(List::size).sum();
        return "Astralis native spawns: " + current.sourceRuleCount() + " source rules, "
                + compiledRules + " category rules, " + current.controlledMobs().size() + " controlled mobs, "
                + current.disabledNaturalMobs().size() + " disabled mobs; surface x"
                + current.surfaceMultiplier() + ", caves x" + current.caveMultiplier() + ".";
    }

    public static void beginNativeSpawn(ServerLevel level) {
        ACTIVE_NATIVE_SPAWN_LEVEL.set(level);
    }

    public static void endNativeSpawn() {
        ACTIVE_NATIVE_SPAWN_LEVEL.remove();
    }

    public static Integer categoryCap(MobCategory category) {
        ServerLevel level = ACTIVE_NATIVE_SPAWN_LEVEL.get();
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        if (level == null || !isOverworld(level) || !current.enabled()) {
            return null;
        }
        return current.categoryCaps().get(category);
    }

    public static BlockPos creatureSurfaceCandidate(ServerLevel level, BlockPos horizontalPos) {
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        if (!isOverworld(level) || !current.enabled()) return null;
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontalPos);
        if (isProtected(current, level.getBiome(surface)) || isProtected(current, level.getBiome(horizontalPos))) return null;
        // Keep half the ordinary, full-height attempts for cave and amphibious creatures.
        return level.random.nextBoolean() ? surface : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isOverworld(level)) {
            return;
        }
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        if (!current.enabled()) {
            return;
        }

        BlockPos pos = event.getPos();
        MobCategory category = event.getMobCategory();
        if (category == MobCategory.MONSTER && isVillage(level, pos)) {
            clear(event);
            return;
        }
        if (isProtected(current, level.getBiome(pos))) {
            return;
        }
        if (isStructureOverride(level, category, pos, event.getSpawnerDataList())) {
            return;
        }

        List<OverworldSpawnConfig.CompiledRule> matches = matchingRules(current, level, pos, category);
        double multiplier = spawnMultiplier(current, matches);
        if (isSurfaceNightMonster(current, level, pos, category)) {
            multiplier *= current.surfaceNightMonsterMultiplier();
        }
        if (multiplier < 1.0D && !passesRateGate(level, pos, category, multiplier)) {
            clear(event);
            return;
        }

        removeDenied(event, current.deniedNaturalMobs());
        Map<EntityType<?>, Integer> counts = entityCounts(level);
        if (atHabitatCap(current, level, pos, category)) return;
        for (OverworldSpawnConfig.CompiledRule rule : matches) {
            for (OverworldSpawnConfig.Choice choice : rule.choices()) {
                if (counts.getOrDefault(choice.type(), 0) >= maximumFor(current, rule, choice.id())) {
                    continue;
                }
                if (!passesSpecialPlacement(level, pos, choice.id())) {
                    continue;
                }
                if (choice.nativeBiomePool()) {
                    spawnEntries(level, pos, category, choice).forEach(event::addSpawnerData);
                } else {
                    event.addSpawnerData(choice.spawnerData());
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (!isOverworld(level)) {
            return;
        }
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        if (!current.enabled()) {
            return;
        }

        BlockPos pos = event.getPos();
        EntityType<?> entityType = event.getEntityType();
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        if (entityType.getCategory() == MobCategory.MONSTER && isVillage(level, pos)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        if (isProtected(current, level.getBiome(pos))) {
            return;
        }
        if (event.getSpawnType() == MobSpawnType.NATURAL && isStructureOverride(level, entityType.getCategory(), pos, null)) {
            return;
        }
        if (entityId == null || !current.deniedNaturalMobs().contains(entityId)) {
            return;
        }
        if (event.getSpawnType() == MobSpawnType.CHUNK_GENERATION) {
            event.setResult(Event.Result.DENY);
            return;
        }

        List<OverworldSpawnConfig.CompiledRule> matches = matchingRules(current, level, pos, entityType.getCategory());
        double multiplier = spawnMultiplier(current, matches);
        if ((multiplier < 1.0D && !passesRateGate(level, pos, entityType.getCategory(), multiplier))
                || !hasAllowedChoice(level, pos, entityType, entityId, matches)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        if (matches.stream().anyMatch(rule -> rule.noRestrictions() && contains(rule, entityType))) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (!isOverworld(level)) {
            return;
        }
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        EntityType<?> type = event.getEntity().getType();
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        BlockPos pos = event.getEntity().blockPosition();
        if (!current.enabled() || entityId == null || !current.deniedNaturalMobs().contains(entityId)
                || isProtected(current, level.getBiome(pos))
                || isStructureOverride(level, type.getCategory(), pos, null)) {
            return;
        }
        List<OverworldSpawnConfig.CompiledRule> matches = matchingRules(current, level, pos, type.getCategory());
        if (!hasAllowedChoice(level, pos, type, entityId, matches)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        if (matches.stream().anyMatch(rule -> rule.noRestrictions() && contains(rule, type))) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (!isOverworld(level)) {
            return;
        }
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (!current.enabled() || entityId == null || isProtected(current, level.getBiome(event.getEntity().blockPosition()))) {
            return;
        }

        Set<String> tags = current.scoreboardTagsByMob().get(entityId);
        if (tags != null) {
            tags.forEach(event.getEntity()::addTag);
        }
        incrementEntityCount(level, event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isOverworld(level)) {
            return;
        }
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (HAMSTER.equals(entityId)) {
            event.setCanceled(true);
        }
    }

    private static OverworldSpawnConfig.RuntimeConfig currentConfig() {
        if (!loaded) {
            reload();
        }
        return config;
    }

    private static List<OverworldSpawnConfig.CompiledRule> matchingRules(
            OverworldSpawnConfig.RuntimeConfig current,
            ServerLevel level,
            BlockPos pos,
            MobCategory category
    ) {
        Holder<Biome> biome = level.getBiome(pos);
        ResourceLocation biomeId = biome.unwrapKey().map(ResourceKey::location).orElse(null);
        List<OverworldSpawnConfig.CompiledRule> categoryRules = current.rulesByCategory().getOrDefault(category, List.of());
        if (categoryRules.isEmpty()) {
            return List.of();
        }
        if (!hasOpenEnvironment(level, pos) && !hasNearbyCavePlayer(level, pos)) {
            return List.of();
        }

        List<OverworldSpawnConfig.CompiledRule> staticMatches;
        if (biomeId == null) {
            staticMatches = categoryRules.stream().filter(rule -> matchesBiome(rule, biome, null)).toList();
        } else {
            StaticRuleKey key = new StaticRuleKey(biomeId, category);
            synchronized (STATIC_RULE_CACHE) {
                staticMatches = STATIC_RULE_CACHE.computeIfAbsent(key,
                        ignored -> categoryRules.stream().filter(rule -> matchesBiome(rule, biome, biomeId)).toList());
            }
        }

        List<OverworldSpawnConfig.CompiledRule> result = new ArrayList<>();
        for (OverworldSpawnConfig.CompiledRule rule : staticMatches) {
            if (rule.choices().stream().anyMatch(choice -> !choice.nativeBiomePool()
                    || !spawnEntries(level, pos, category, choice).isEmpty())
                    && matchesDynamic(rule, level, pos)) {
                result.add(rule);
            }
        }
        return result;
    }

    private static boolean matchesBiome(OverworldSpawnConfig.CompiledRule rule, Holder<Biome> biome, ResourceLocation biomeId) {
        if (!rule.biomes().isEmpty() && (biomeId == null || !rule.biomes().contains(biomeId))) {
            return false;
        }
        if (!rule.biomeTags().isEmpty() && rule.biomeTags().stream().noneMatch(biome::is)) {
            return false;
        }
        if (biomeId != null && rule.excludedBiomes().contains(biomeId)) {
            return false;
        }
        return rule.excludedBiomeTags().stream().noneMatch(biome::is);
    }

    private static boolean matchesDynamic(OverworldSpawnConfig.CompiledRule rule, ServerLevel level, BlockPos pos) {
        int y = pos.getY();
        if (rule.minimumHeight() != null && y < rule.minimumHeight()) return false;
        if (rule.maximumHeight() != null && y > rule.maximumHeight()) return false;

        if (!matchesDistance(rule, level, pos)) return false;
        if (rule.maximumBlockLight() != null && level.getBrightness(LightLayer.BLOCK, pos) > rule.maximumBlockLight()) return false;
        if (rule.maximumFullLight() != null && level.getMaxLocalRawBrightness(pos) > rule.maximumFullLight()) return false;
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        if (rule.minimumSkyLight() != null && skyLight < rule.minimumSkyLight()) return false;
        if (rule.maximumSkyLight() != null && skyLight > rule.maximumSkyLight()) return false;
        if (!matchesTime(rule, level.getDayTime() % 24_000L)) return false;
        if (rule.seesSky() != null && level.canSeeSky(pos) != rule.seesSky()) return false;
        if (rule.excludesCaves() && !NaturalSurfaceSpawnSupport.hasNaturalSkyAccess(level, pos)) return false;
        if (!matchesStructures(rule.structureTags(), level, pos, true)) return false;
        if (!matchesStructures(rule.excludedStructureTags(), level, pos, false)) return false;
        if (rule.sturdy() && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        if (rule.inWater() && !level.getFluidState(pos).is(FluidTags.WATER)) return false;
        return !rule.inAir() || level.getBlockState(pos).isAir();
    }

    private static boolean matchesDistance(OverworldSpawnConfig.CompiledRule rule, ServerLevel level, BlockPos pos) {
        if (rule.minimumDistance() == null && rule.maximumDistance() == null) {
            return true;
        }
        double maximum = rule.maximumDistance() == null ? -1.0D : rule.maximumDistance();
        net.minecraft.world.entity.player.Player player = level.getNearestPlayer(
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, maximum, false
        );
        if (player == null) {
            return false;
        }
        if (rule.minimumDistance() == null) {
            return true;
        }
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)
                >= (double) rule.minimumDistance() * rule.minimumDistance();
    }

    private static boolean matchesTime(OverworldSpawnConfig.CompiledRule rule, long time) {
        if (rule.minimumTime() == null && rule.maximumTime() == null) {
            return true;
        }
        long minimum = rule.minimumTime() == null ? 0L : rule.minimumTime();
        long maximum = rule.maximumTime() == null ? 23_999L : rule.maximumTime();
        return minimum <= maximum ? time >= minimum && time <= maximum : time >= minimum || time <= maximum;
    }

    private static boolean matchesStructures(List<TagKey<Structure>> tags, ServerLevel level, BlockPos pos, boolean positive) {
        if (tags.isEmpty()) {
            return true;
        }
        boolean found = tags.stream().anyMatch(tag -> level.structureManager().getStructureWithPieceAt(pos, tag).isValid());
        return positive == found;
    }

    private static boolean hasAllowedChoice(
            ServerLevel level,
            BlockPos pos,
            EntityType<?> type,
            ResourceLocation entityId,
            List<OverworldSpawnConfig.CompiledRule> matches
    ) {
        OverworldSpawnConfig.RuntimeConfig current = currentConfig();
        if (atHabitatCap(current, level, pos, type.getCategory())) return false;
        int currentCount = entityCounts(level).getOrDefault(type, 0);
        for (OverworldSpawnConfig.CompiledRule rule : matches) {
            if (currentCount >= maximumFor(current, rule, entityId)) {
                continue;
            }
            if (rule.choices().stream().anyMatch(choice -> choice.type() == type
                    && (!choice.nativeBiomePool() || !spawnEntries(level, pos, type.getCategory(), choice).isEmpty()))
                    && passesSpecialPlacement(level, pos, entityId)) {
                return true;
            }
        }
        return false;
    }

    private static int maximumFor(OverworldSpawnConfig.RuntimeConfig current,
                                  OverworldSpawnConfig.CompiledRule rule, ResourceLocation id) {
        return Math.min(rule.maximumThis() == null ? Integer.MAX_VALUE : rule.maximumThis(),
                current.mobCaps().getOrDefault(id, Integer.MAX_VALUE));
    }

    private static String habitat(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) return "water";
        return NaturalSurfaceSpawnSupport.hasNaturalSkyAccess(level, pos) ? "surface" : "cave";
    }

    private static boolean isSurfaceNightMonster(OverworldSpawnConfig.RuntimeConfig current,
                                                 ServerLevel level, BlockPos pos, MobCategory category) {
        return category == MobCategory.MONSTER
                && current.surfaceNightMonsterMultiplier() < 1.0D
                && level.isNight()
                && "surface".equals(habitat(level, pos));
    }

    private static boolean hasOpenEnvironment(ServerLevel level, BlockPos pos) {
        // Below open ocean water is not a cave; foliage is also allowed above surface players.
        return level.canSeeSkyFromBelowWater(pos) || NaturalSurfaceSpawnSupport.hasNaturalSkyAccess(level, pos);
    }

    private static boolean hasNearbyCavePlayer(ServerLevel level, BlockPos pos) {
        long tick = level.getGameTime();
        CavePlayerSnapshot snapshot = CAVE_PLAYERS.get(level);
        if (snapshot == null || snapshot.tick() != tick) {
            List<Vec3> players = level.players().stream()
                    .filter(player -> !player.isSpectator() && player.isAlive()
                            && !hasOpenEnvironment(level, player.blockPosition()))
                    .map(Entity::position).toList();
            snapshot = new CavePlayerSnapshot(tick, players);
            CAVE_PLAYERS.put(level, snapshot);
        }
        Vec3 center = Vec3.atCenterOf(pos);
        return snapshot.positions().stream().anyMatch(player -> player.distanceToSqr(center) <= 96.0D * 96.0D);
    }

    private static boolean atHabitatCap(OverworldSpawnConfig.RuntimeConfig current,
                                       ServerLevel level, BlockPos pos, MobCategory category) {
        if (current.habitatCaps().values().stream().noneMatch(caps -> caps.containsKey(category))) return false;
        String habitat = habitat(level, pos);
        Integer cap = current.habitatCaps().getOrDefault(habitat, Map.of()).get(category);
        if (cap == null) return false;
        if (category == MobCategory.MONSTER && "surface".equals(habitat) && level.isNight()) {
            // Fewer attempts alone only slow the refill; scale the surface population too.
            cap = (int) Math.round(cap * current.surfaceNightMonsterMultiplier());
        }
        entityCounts(level);
        return ENTITY_COUNTS.get(level).habitats.getOrDefault(new HabitatKey(habitat, category), 0) >= cap;
    }

    private static boolean contains(OverworldSpawnConfig.CompiledRule rule, EntityType<?> type) {
        return rule.choices().stream().anyMatch(choice -> choice.type() == type);
    }

    private static List<MobSpawnSettings.SpawnerData> spawnEntries(ServerLevel level, BlockPos pos,
            MobCategory category, OverworldSpawnConfig.Choice choice) {
        if (!choice.nativeBiomePool()) return List.of(choice.spawnerData());
        // Read the biome source, not the mutable event list: native entries are removed before re-adding.
        return level.getBiome(pos).value().getMobSettings().getMobs(category).unwrap().stream()
                .filter(entry -> entry.type == choice.type()).toList();
    }

    private static EntityType<?> populationType(EntityType<?> type) {
        // Icy's replaces a naturally spawned vanilla horse with its breed entity after finalization.
        return type.is(ICY_BREED_HORSES) ? EntityType.HORSE : type;
    }

    private static boolean passesSpecialPlacement(ServerLevel level, BlockPos pos, ResourceLocation entityId) {
        if (level.getBlockState(pos.below()).is(BlockTags.LEAVES)) {
            return false;
        }
        if (FLUTTER.equals(entityId) && !level.getBlockState(pos.below()).is(FLUTTER_SPAWNS)) {
            return false;
        }
        if (GRAZER.equals(entityId)) {
            if (level.getBlockState(pos.above(2)).is(BlockTags.MINEABLE_WITH_PICKAXE)
                    || level.getBlockState(pos.above(2)).is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                return false;
            }
        }
        if (BENTHIC_MOBS.contains(entityId) && level.getBlockState(pos.below()).is(BENTHIC_PASS_THROUGH)) {
            return false;
        }
        return !SHORELINE_MOBS.contains(entityId) || hasNearbyWater(level, pos);
    }

    private static boolean hasNearbyWater(ServerLevel level, BlockPos pos) {
        for (int[] offset : SHORELINE_OFFSETS) {
            if (level.getFluidState(pos.offset(offset[0], offset[1], offset[2])).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static double spawnMultiplier(
            OverworldSpawnConfig.RuntimeConfig current,
            List<OverworldSpawnConfig.CompiledRule> matches
    ) {
        double multiplier = 1.0D;
        for (OverworldSpawnConfig.CompiledRule rule : matches) {
            if (rule.sourceClass() == OverworldSpawnConfig.SourceClass.SURFACE_LAND) {
                multiplier = Math.min(multiplier, current.surfaceMultiplier());
            } else if (rule.sourceClass() == OverworldSpawnConfig.SourceClass.CAVE) {
                multiplier = Math.min(multiplier, current.caveMultiplier());
            }
        }
        return multiplier;
    }

    private static boolean passesRateGate(ServerLevel level, BlockPos pos, MobCategory category, double multiplier) {
        if (multiplier <= 0.0D) return false;
        if (multiplier >= 1.0D) return true;
        long value = level.getSeed();
        value ^= level.getGameTime() * 0x9E3779B97F4A7C15L;
        value ^= ChunkPos.asLong(pos) * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) category.ordinal() * 0x165667B19E3779F9L;
        value = mix64(value);
        double unit = (value >>> 11) * 0x1.0p-53;
        return unit < multiplier;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static boolean isProtected(OverworldSpawnConfig.RuntimeConfig current, Holder<Biome> biome) {
        return current.protectedBiomeTags().stream().anyMatch(biome::is);
    }

    private static boolean isVillage(ServerLevel level, BlockPos pos) {
        return level.isVillage(pos)
                || level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
    }

    private static boolean isStructureOverride(
            ServerLevel level,
            MobCategory category,
            BlockPos pos,
            List<MobSpawnSettings.SpawnerData> suppliedList
    ) {
        if (category == MobCategory.MISC) {
            return false;
        }
        Holder<Biome> biome = level.getBiome(pos);
        List<MobSpawnSettings.SpawnerData> base = biome.value().getMobSettings().getMobs(category).unwrap();
        List<MobSpawnSettings.SpawnerData> actual = suppliedList;
        if (actual == null) {
            actual = level.getChunkSource().getGenerator()
                    .getMobsAt(biome, level.structureManager(), category, pos)
                    .unwrap();
        }
        return !actual.equals(base);
    }

    private static void removeDenied(LevelEvent.PotentialSpawns event, Set<ResourceLocation> denied) {
        for (MobSpawnSettings.SpawnerData data : List.copyOf(event.getSpawnerDataList())) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(data.type);
            if (id != null && denied.contains(id)) {
                event.removeSpawnerData(data);
            }
        }
    }

    private static void clear(LevelEvent.PotentialSpawns event) {
        for (MobSpawnSettings.SpawnerData data : List.copyOf(event.getSpawnerDataList())) {
            event.removeSpawnerData(data);
        }
    }

    private static Map<EntityType<?>, Integer> entityCounts(ServerLevel level) {
        synchronized (ENTITY_COUNTS) {
            CountSnapshot snapshot = ENTITY_COUNTS.computeIfAbsent(level, ignored -> new CountSnapshot());
            long gameTime = level.getGameTime();
            if (snapshot.lastRefresh == Long.MIN_VALUE || gameTime < snapshot.lastRefresh || gameTime - snapshot.lastRefresh >= 20L) {
                snapshot.counts.clear();
                snapshot.habitats.clear();
                for (Entity entity : level.getAllEntities()) {
                    snapshot.counts.merge(populationType(entity.getType()), 1, Integer::sum);
                    if (entity instanceof Mob mob && !mob.isPersistenceRequired() && !mob.requiresCustomPersistence()
                            && (mob.getType().getCategory() == MobCategory.MONSTER || mob.getType().getCategory() == MobCategory.CREATURE)
                            && !isProtected(currentConfig(), level.getBiome(mob.blockPosition()))) {
                        snapshot.habitats.merge(new HabitatKey(habitat(level, mob.blockPosition()), mob.getType().getCategory()), 1, Integer::sum);
                    }
                }
                snapshot.lastRefresh = gameTime;
            }
            return snapshot.counts;
        }
    }

    private static void incrementEntityCount(ServerLevel level, Mob mob) {
        synchronized (ENTITY_COUNTS) {
            CountSnapshot snapshot = ENTITY_COUNTS.get(level);
            if (snapshot != null) {
                snapshot.counts.merge(populationType(mob.getType()), 1, Integer::sum);
                if (mob.getType().getCategory() == MobCategory.MONSTER || mob.getType().getCategory() == MobCategory.CREATURE) {
                    snapshot.habitats.merge(new HabitatKey(habitat(level, mob.blockPosition()), mob.getType().getCategory()), 1, Integer::sum);
                }
            }
        }
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && level.dimension() == Level.OVERWORLD;
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private static Set<ResourceLocation> ids(String... values) {
        Set<ResourceLocation> result = new HashSet<>();
        for (String value : values) {
            result.add(new ResourceLocation(value));
        }
        return Set.copyOf(result);
    }

    private record StaticRuleKey(ResourceLocation biome, MobCategory category) {
    }

    private record HabitatKey(String habitat, MobCategory category) {
    }

    private record CavePlayerSnapshot(long tick, List<Vec3> positions) {
    }

    private static final class CountSnapshot {
        private long lastRefresh = Long.MIN_VALUE;
        private final Map<EntityType<?>, Integer> counts = new IdentityHashMap<>();
        private final Map<HabitatKey, Integer> habitats = new HashMap<>();
    }

}
