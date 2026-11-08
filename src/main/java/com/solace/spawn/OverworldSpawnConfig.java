package com.solace.spawn;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OverworldSpawnConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("solace-overworld-spawns.json");
    private static final int WEIGHT_SCALE = 1_000;

    private OverworldSpawnConfig() {
    }

    static RuntimeConfig load() {
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int format = integer(root, "format", 0);
            if (format != 1) {
                throw new IllegalStateException("unsupported format " + format);
            }

            Set<ResourceLocation> controlled = ids(root.getAsJsonArray("controlled_mobs"));
            Set<ResourceLocation> denied = ids(root.getAsJsonArray("denied_natural_mobs"));
            Set<ResourceLocation> disabled = ids(root.getAsJsonArray("disabled_natural_mobs"));
            List<TagKey<Biome>> protectedBiomes = biomeTags(root.getAsJsonArray("protected_biome_tags"));
            Map<MobCategory, Integer> categoryCaps = categoryCaps(root.getAsJsonObject("category_caps"));
            Map<String, Map<MobCategory, Integer>> habitatCaps = new HashMap<>();
            JsonObject habitats = conditionsObject(root, "habitat_caps");
            for (String habitat : List.of("surface", "cave", "water")) {
                habitatCaps.put(habitat, Map.copyOf(categoryCaps(habitats.getAsJsonObject(habitat))));
            }
            Map<ResourceLocation, Integer> mobCaps = new HashMap<>();
            conditionsObject(root, "mob_caps").entrySet().forEach(entry ->
                    mobCaps.put(new ResourceLocation(entry.getKey()), Math.max(1, entry.getValue().getAsInt())));
            double surfaceMultiplier = decimal(root, "surface_land_spawn_multiplier", 1.0D);
            double caveMultiplier = decimal(root, "cave_spawn_multiplier", 1.0D);
            double surfaceNightMonsterMultiplier = Math.max(0.0D, Math.min(1.0D,
                    decimal(root, "surface_night_monster_multiplier", 1.0D)));

            EnumMap<MobCategory, List<CompiledRule>> byCategory = new EnumMap<>(MobCategory.class);
            Map<ResourceLocation, Set<String>> tagsByMob = new HashMap<>();
            int missingEntityTypes = 0;
            int miscEntityTypes = 0;
            int sourceIndex = 0;
            for (JsonElement element : root.getAsJsonArray("rules")) {
                sourceIndex++;
                ParsedRule parsed = parseRule(sourceIndex, element.getAsJsonObject());
                if (parsed == null) {
                    continue;
                }

                EnumMap<MobCategory, List<Choice>> choicesByCategory = new EnumMap<>(MobCategory.class);
                for (ParsedChoice parsedChoice : parsed.choices) {
                    if (!ForgeRegistries.ENTITY_TYPES.containsKey(parsedChoice.id)) {
                        missingEntityTypes++;
                        LOGGER.warn("[Solace Spawns] Rule {} references missing entity type {}.", sourceIndex, parsedChoice.id);
                        continue;
                    }
                    EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(parsedChoice.id);
                    if (entityType == null) {
                        missingEntityTypes++;
                        continue;
                    }
                    if (entityType.getCategory() == MobCategory.MISC) {
                        miscEntityTypes++;
                        LOGGER.warn("[Solace Spawns] {} is MISC and cannot use the natural mob scheduler.", parsedChoice.id);
                        continue;
                    }

                    int nativeWeight = Math.max(1, (int) Math.round(
                            parsed.rate * WEIGHT_SCALE * parsedChoice.selectionWeight / parsed.selectionWeightTotal
                    ));
                    MobSpawnSettings.SpawnerData data = new MobSpawnSettings.SpawnerData(
                            entityType,
                            nativeWeight,
                            parsed.minimumGroup,
                            parsed.maximumGroup
                    );
                    Choice choice = new Choice(parsedChoice.id, entityType, data,
                            booleanValue(element.getAsJsonObject(), "native_biome_pool", false));
                    choicesByCategory.computeIfAbsent(entityType.getCategory(), ignored -> new ArrayList<>()).add(choice);
                    if (!parsed.scoreboardTags.isEmpty()) {
                        tagsByMob.computeIfAbsent(parsedChoice.id, ignored -> new LinkedHashSet<>())
                                .addAll(parsed.scoreboardTags);
                    }
                }

                for (Map.Entry<MobCategory, List<Choice>> entry : choicesByCategory.entrySet()) {
                    CompiledRule rule = parsed.compile(entry.getKey(), entry.getValue());
                    byCategory.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(rule);
                }
            }

            byCategory.replaceAll((category, rules) -> List.copyOf(rules));
            Map<ResourceLocation, Set<String>> immutableTags = new HashMap<>();
            tagsByMob.forEach((id, tags) -> immutableTags.put(id, Set.copyOf(tags)));
            RuntimeConfig config = new RuntimeConfig(
                    true,
                    surfaceMultiplier,
                    caveMultiplier,
                    surfaceNightMonsterMultiplier,
                    Map.copyOf(categoryCaps),
                    Map.copyOf(habitatCaps),
                    Map.copyOf(mobCaps),
                    Set.copyOf(controlled),
                    Set.copyOf(denied),
                    Set.copyOf(disabled),
                    List.copyOf(protectedBiomes),
                    Collections.unmodifiableMap(byCategory),
                    Map.copyOf(immutableTags),
                    sourceIndex,
                    missingEntityTypes,
                    miscEntityTypes
            );
            LOGGER.info("[Solace Spawns] Loaded {} rules for {} controlled mobs ({} denied, {} disabled, {} missing, {} MISC).",
                    sourceIndex, controlled.size(), denied.size(), disabled.size(), missingEntityTypes, miscEntityTypes);
            return config;
        } catch (Exception exception) {
            LOGGER.error("[Solace Spawns] Could not load {}. Native spawn migration is disabled for this launch.", CONFIG_PATH, exception);
            return RuntimeConfig.disabled();
        }
    }

    private static ParsedRule parseRule(int sourceIndex, JsonObject object) {
        JsonObject conditions = object.getAsJsonObject("conditions");
        if (conditions == null || !"minecraft:overworld".equals(string(conditions, "dimension", ""))) {
            return null;
        }

        List<ResourceLocation> mobIds = resourceLocations(object.get("mob"));
        if (mobIds.isEmpty()) {
            return null;
        }
        List<Integer> configuredWeights = integers(object.get("weights"));
        List<ParsedChoice> choices = new ArrayList<>(mobIds.size());
        int weightTotal = 0;
        for (int index = 0; index < mobIds.size(); index++) {
            int selectionWeight = index < configuredWeights.size() ? Math.max(1, configuredWeights.get(index)) : 1;
            choices.add(new ParsedChoice(mobIds.get(index), selectionWeight));
            weightTotal += selectionWeight;
        }

        JsonObject amount = conditionsObject(object, "amount");
        JsonObject and = conditionsObject(conditions, "and");
        JsonObject not = conditionsObject(conditions, "not");
        SourceClass sourceClass = classify(conditions, and, not);

        return new ParsedRule(
                sourceIndex,
                decimal(object, "persecond", 0.0D),
                integer(amount, "minimum", 1),
                integer(amount, "maximum", 1),
                integer(amount, "groupdistance", 6),
                choices,
                Math.max(1, weightTotal),
                stringSet(object.get("addscoreboardtags")),
                sourceClass,
                integerOrNull(conditions, "mindist"),
                integerOrNull(conditions, "maxdist"),
                integerOrNull(conditions, "minheight"),
                integerOrNull(conditions, "maxheight"),
                integerOrNull(conditions, "maxthis"),
                booleanValue(conditions, "sturdy", false),
                booleanValue(conditions, "inwater", false),
                booleanValue(conditions, "inair", false),
                booleanValue(conditions, "norestrictions", false),
                resourceLocationSet(and.get("biome")),
                biomeTags(and.get("biometags")),
                structureTags(and.get("structuretags")),
                integerOrNull(and, "maxlight"),
                integerOrNull(and, "maxlight_full"),
                integerOrNull(and, "minlight_sky"),
                integerOrNull(and, "maxlight_sky"),
                integerOrNull(and, "mintime"),
                integerOrNull(and, "maxtime"),
                booleanOrNull(and, "seesky"),
                resourceLocationSet(not.get("biome")),
                biomeTags(not.get("biometags")),
                structureTags(not.get("structuretags")),
                booleanValue(not, "cave", false)
        );
    }

    private static SourceClass classify(JsonObject conditions, JsonObject and, JsonObject not) {
        if (booleanValue(not, "cave", false) && !booleanValue(conditions, "inwater", false)) {
            return SourceClass.SURFACE_LAND;
        }
        Boolean seesSky = booleanOrNull(and, "seesky");
        return Boolean.FALSE.equals(seesSky) ? SourceClass.CAVE : SourceClass.OTHER;
    }

    private static Map<MobCategory, Integer> categoryCaps(JsonObject object) {
        EnumMap<MobCategory, Integer> result = new EnumMap<>(MobCategory.class);
        if (object == null) {
            return result;
        }
        for (MobCategory category : MobCategory.values()) {
            if (object.has(category.getName())) {
                result.put(category, Math.max(1, object.get(category.getName()).getAsInt()));
            }
        }
        return result;
    }

    private static Set<ResourceLocation> ids(JsonArray array) {
        return array == null ? Set.of() : Set.copyOf(resourceLocationSet(array));
    }

    private static List<TagKey<Biome>> biomeTags(JsonElement element) {
        List<TagKey<Biome>> result = new ArrayList<>();
        for (ResourceLocation id : resourceLocations(element)) {
            result.add(TagKey.create(Registries.BIOME, id));
        }
        return result;
    }

    private static List<TagKey<Structure>> structureTags(JsonElement element) {
        List<TagKey<Structure>> result = new ArrayList<>();
        for (ResourceLocation id : resourceLocations(element)) {
            result.add(TagKey.create(Registries.STRUCTURE, id));
        }
        return result;
    }

    private static Set<ResourceLocation> resourceLocationSet(JsonElement element) {
        return Set.copyOf(resourceLocations(element));
    }

    private static List<ResourceLocation> resourceLocations(JsonElement element) {
        List<ResourceLocation> result = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return result;
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                result.add(new ResourceLocation(value.getAsString()));
            }
        } else {
            result.add(new ResourceLocation(element.getAsString()));
        }
        return result;
    }

    private static Set<String> stringSet(JsonElement element) {
        Set<String> result = new LinkedHashSet<>();
        if (element == null || element.isJsonNull()) {
            return result;
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                result.add(value.getAsString());
            }
        } else {
            result.add(element.getAsString());
        }
        return result;
    }

    private static List<Integer> integers(JsonElement element) {
        List<Integer> result = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return result;
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                result.add(value.getAsInt());
            }
        } else {
            result.add(element.getAsInt());
        }
        return result;
    }

    private static JsonObject conditionsObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        Integer value = integerOrNull(object, key);
        return value == null ? fallback : value;
    }

    private static Integer integerOrNull(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsInt();
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsDouble();
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        Boolean value = booleanOrNull(object, key);
        return value == null ? fallback : value;
    }

    private static Boolean booleanOrNull(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsBoolean();
    }

    enum SourceClass {
        SURFACE_LAND,
        CAVE,
        OTHER
    }

    record RuntimeConfig(
            boolean enabled,
            double surfaceMultiplier,
            double caveMultiplier,
            double surfaceNightMonsterMultiplier,
            Map<MobCategory, Integer> categoryCaps,
            Map<String, Map<MobCategory, Integer>> habitatCaps,
            Map<ResourceLocation, Integer> mobCaps,
            Set<ResourceLocation> controlledMobs,
            Set<ResourceLocation> deniedNaturalMobs,
            Set<ResourceLocation> disabledNaturalMobs,
            List<TagKey<Biome>> protectedBiomeTags,
            Map<MobCategory, List<CompiledRule>> rulesByCategory,
            Map<ResourceLocation, Set<String>> scoreboardTagsByMob,
            int sourceRuleCount,
            int missingEntityTypes,
            int miscEntityTypes
    ) {
        static RuntimeConfig disabled() {
            return new RuntimeConfig(false, 1.0D, 1.0D, 1.0D, Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), List.of(), Map.of(), Map.of(), 0, 0, 0);
        }
    }

    record Choice(ResourceLocation id, EntityType<?> type, MobSpawnSettings.SpawnerData spawnerData,
                  boolean nativeBiomePool) {
    }

    record CompiledRule(
            int sourceIndex,
            MobCategory category,
            double rate,
            int minimumGroup,
            int maximumGroup,
            int groupDistance,
            List<Choice> choices,
            Set<String> scoreboardTags,
            SourceClass sourceClass,
            Integer minimumDistance,
            Integer maximumDistance,
            Integer minimumHeight,
            Integer maximumHeight,
            Integer maximumThis,
            boolean sturdy,
            boolean inWater,
            boolean inAir,
            boolean noRestrictions,
            Set<ResourceLocation> biomes,
            List<TagKey<Biome>> biomeTags,
            List<TagKey<Structure>> structureTags,
            Integer maximumBlockLight,
            Integer maximumFullLight,
            Integer minimumSkyLight,
            Integer maximumSkyLight,
            Integer minimumTime,
            Integer maximumTime,
            Boolean seesSky,
            Set<ResourceLocation> excludedBiomes,
            List<TagKey<Biome>> excludedBiomeTags,
            List<TagKey<Structure>> excludedStructureTags,
            boolean excludesCaves
    ) {
    }

    private record ParsedChoice(ResourceLocation id, int selectionWeight) {
    }

    private record ParsedRule(
            int sourceIndex,
            double rate,
            int minimumGroup,
            int maximumGroup,
            int groupDistance,
            List<ParsedChoice> choices,
            int selectionWeightTotal,
            Set<String> scoreboardTags,
            SourceClass sourceClass,
            Integer minimumDistance,
            Integer maximumDistance,
            Integer minimumHeight,
            Integer maximumHeight,
            Integer maximumThis,
            boolean sturdy,
            boolean inWater,
            boolean inAir,
            boolean noRestrictions,
            Set<ResourceLocation> biomes,
            List<TagKey<Biome>> biomeTags,
            List<TagKey<Structure>> structureTags,
            Integer maximumBlockLight,
            Integer maximumFullLight,
            Integer minimumSkyLight,
            Integer maximumSkyLight,
            Integer minimumTime,
            Integer maximumTime,
            Boolean seesSky,
            Set<ResourceLocation> excludedBiomes,
            List<TagKey<Biome>> excludedBiomeTags,
            List<TagKey<Structure>> excludedStructureTags,
            boolean excludesCaves
    ) {
        CompiledRule compile(MobCategory category, List<Choice> compiledChoices) {
            return new CompiledRule(
                    sourceIndex, category, rate, minimumGroup, maximumGroup, groupDistance,
                    List.copyOf(compiledChoices), scoreboardTags, sourceClass, minimumDistance, maximumDistance,
                    minimumHeight, maximumHeight, maximumThis, sturdy, inWater, inAir, noRestrictions,
                    biomes, biomeTags, structureTags, maximumBlockLight, maximumFullLight, minimumSkyLight,
                    maximumSkyLight, minimumTime, maximumTime, seesSky, excludedBiomes, excludedBiomeTags,
                    excludedStructureTags, excludesCaves
            );
        }
    }
}
