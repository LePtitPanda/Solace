import com.mojang.serialization.Lifecycle;
import com.solace.worldgen.FeatureCycleResolver;
import com.solace.compat.performance.CommonPerformancePatches;
import com.solace.mixin.SolaceMixinPlugin;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises the actual mapped 1.20.1 classes without launching a client or server. */
public final class VerifyFeatureCycleResolver {
    private static final List<Holder<PlacedFeature>> FEATURES = new ArrayList<>();
    private static final List<String> WARNINGS = Collections.synchronizedList(new ArrayList<>());

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static HolderSet<PlacedFeature> features(int... indices) {
        List<Holder<PlacedFeature>> holders = new ArrayList<>();
        for (int index : indices) holders.add(FEATURES.get(index));
        return HolderSet.direct(holders);
    }

    private static List<FeatureSorter.StepFeatureData> vanilla(List<List<HolderSet<PlacedFeature>>> biomes) {
        return FeatureSorter.buildFeaturesPerStep(biomes, biome -> biome, true);
    }

    private static List<FeatureSorter.StepFeatureData> fallback(List<List<HolderSet<PlacedFeature>>> biomes) {
        return FeatureCycleResolver.withFallback(biomes, biome -> biome, () -> vanilla(biomes));
    }

    private static void same(List<FeatureSorter.StepFeatureData> expected, List<FeatureSorter.StepFeatureData> actual) {
        check(expected.size() == actual.size(), "Step count changed");
        for (int step = 0; step < expected.size(); step++) {
            List<PlacedFeature> wanted = expected.get(step).features();
            List<PlacedFeature> got = actual.get(step).features();
            check(wanted.size() == got.size(), "Feature count changed at step " + step);
            for (int index = 0; index < wanted.size(); index++) {
                check(wanted.get(index) == got.get(index), "Feature identity/order changed at step " + step);
                check(actual.get(step).indexMapping().applyAsInt(got.get(index)) == index, "Wrong feature index");
            }
            PlacedFeature absent = FEATURES.get(FEATURES.size() - 1).value();
            check(actual.get(step).indexMapping().applyAsInt(absent)
                    == expected.get(step).indexMapping().applyAsInt(absent), "Missing-key behavior differs");
        }
    }

    private static void membership(List<List<HolderSet<PlacedFeature>>> biomes, List<FeatureSorter.StepFeatureData> result) {
        int steps = biomes.stream().mapToInt(List::size).max().orElse(0);
        check(result.size() == steps, "Lost empty/trailing steps");
        for (int step = 0; step < steps; step++) {
            Set<PlacedFeature> expected = Collections.newSetFromMap(new IdentityHashMap<>());
            for (List<HolderSet<PlacedFeature>> biome : biomes) {
                if (step < biome.size()) for (Holder<PlacedFeature> holder : biome.get(step)) expected.add(holder.value());
            }
            List<PlacedFeature> actual = result.get(step).features();
            check(actual.size() == expected.size() && expected.containsAll(actual), "Lost or duplicated a feature");
            for (int index = 0; index < actual.size(); index++) {
                check(result.get(step).indexMapping().applyAsInt(actual.get(index)) == index, "Invalid index mapping");
            }
        }
    }

    private static void acyclic() {
        Random random = new Random(0x50FACE);
        for (int trial = 0; trial < 500; trial++) {
            List<List<HolderSet<PlacedFeature>>> biomes = new ArrayList<>();
            for (int biome = 0; biome < 1 + random.nextInt(12); biome++) {
                List<HolderSet<PlacedFeature>> steps = new ArrayList<>();
                for (int step = 0; step < random.nextInt(12); step++) {
                    List<Holder<PlacedFeature>> holders = new ArrayList<>();
                    for (int index = 0; index < 16; index++) {
                        if (random.nextBoolean()) holders.add(FEATURES.get(index));
                    }
                    steps.add(HolderSet.direct(holders));
                }
                biomes.add(steps);
            }
            List<FeatureSorter.StepFeatureData> expected = vanilla(biomes);
            AtomicInteger calls = new AtomicInteger();
            List<FeatureSorter.StepFeatureData> actual = FeatureCycleResolver.withFallback(biomes, biome -> biome, () -> {
                calls.incrementAndGet();
                return expected;
            });
            check(actual == expected && calls.get() == 1, "Acyclic original result was touched");
            same(expected, FeatureCycleResolver.resolve(biomes, biome -> biome));
            membership(biomes, actual);
        }
        same(vanilla(List.of()), fallback(List.of()));
        PlacedFeature original = FEATURES.get(0).value();
        PlacedFeature equal = new PlacedFeature(original.feature(), original.placement());
        check(original != equal && original.equals(equal), "Equal inline fixture must have distinct identity");
        List<List<HolderSet<PlacedFeature>>> aliases = List.of(List.of(features(), features(0)),
                List.of(features(1), HolderSet.direct(Holder.direct(equal))));
        same(vanilla(aliases), FeatureCycleResolver.resolve(aliases, biome -> biome));
        check(WARNINGS.isEmpty(), "Acyclic inputs logged conflicts");
    }

    private static void cyclic() throws Exception {
        List<HolderSet<PlacedFeature>> rainforest = new ArrayList<>(Collections.nCopies(11, features()));
        rainforest.set(0, features(3, 4));
        rainforest.set(9, features(0, 1));
        rainforest.set(10, features(5));
        List<HolderSet<PlacedFeature>> jungle = new ArrayList<>(rainforest);
        jungle.set(9, features(1, 0));
        List<List<HolderSet<PlacedFeature>>> biomes = List.of(rainforest, jungle, jungle, jungle);
        try {
            vanilla(biomes);
            throw new AssertionError("Fixture should produce a real vanilla cycle");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().startsWith("Feature order cycle found"), "Unexpected vanilla failure");
        }

        List<FeatureSorter.StepFeatureData> result = fallback(biomes);
        membership(biomes, result);
        check(result.get(9).features().equals(List.of(FEATURES.get(1).value(), FEATURES.get(0).value())),
                "Should override the one-biome edge and preserve the three-biome edge");
        check(WARNINGS.size() == 1, "Expected one warning per removed edge");
        check(WARNINGS.get(0).equals("[Solace] Resolved feature order cycle: step=9, removed=minecraft:vines -> minecraft:seagrass_river, overriddenBiomes=[source#0]"),
                "Unexpected warning format " + WARNINGS);
        List<List<HolderSet<PlacedFeature>>> repaired = List.of(jungle, jungle, jungle, jungle);
        same(vanilla(repaired), result);
        for (int repeat = 0; repeat < 40; repeat++) same(result, fallback(biomes));

        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<FeatureSorter.StepFeatureData>>> jobs = new ArrayList<>();
            for (int index = 0; index < 32; index++) jobs.add(() -> fallback(biomes));
            for (var future : executor.invokeAll(jobs)) same(result, future.get());
        } finally {
            executor.shutdownNow();
        }

        // Two SCCs, a three-node cycle, repeated features (self-edge), and disconnected features.
        List<List<HolderSet<PlacedFeature>>> complex = List.of(
                List.of(features(0, 1, 2), features(), features(6, 7)),
                List.of(features(2, 0), features(), features(7, 6)),
                List.of(features(8, 8, 9), features(), features(10)),
                List.of(features(11, 12)));
        var complexResult = fallback(complex);
        membership(complex, complexResult);
        for (int repeat = 0; repeat < 20; repeat++) same(complexResult, fallback(complex));

        // Tie by source encounter order, then first-seen feature order, independent of hash iteration.
        Map<String, List<HolderSet<PlacedFeature>>> named = new HashMap<>();
        named.put("test:first", List.of(features(0, 1, 0)));
        named.put("test:second", List.of(features(1, 0)));
        WARNINGS.clear();
        var tied = FeatureCycleResolver.resolve(List.of("test:first", "test:second"), named::get);
        check(tied.get(0).features().equals(List.of(FEATURES.get(1).value(), FEATURES.get(0).value())), "Tie break changed");
        check(WARNINGS.get(0).endsWith("overriddenBiomes=[test:first]"), "Contributor must count each biome once");
    }

    private static void failures() {
        for (RuntimeException failure : List.of(new IllegalStateException("other"), new IllegalStateException(),
                new IllegalArgumentException("Feature order cycle found"))) {
            try {
                FeatureCycleResolver.withFallback(List.of(), ignored -> List.of(), () -> { throw failure; });
                throw new AssertionError("Unrelated failure swallowed");
            } catch (RuntimeException actual) {
                check(actual == failure, "Exception identity changed");
            }
        }
        AtomicInteger getters = new AtomicInteger();
        var result = FeatureCycleResolver.withFallback(List.of("test"), ignored -> {
            getters.incrementAndGet();
            return List.of(features(0));
        }, () -> FeatureCycleResolver.withFallback(List.of(), ignored -> {
            throw new AssertionError("Recursive vanilla witness triggered resolver");
        }, () -> { throw new IllegalStateException("Feature order cycle found"); }));
        check(getters.get() == 1 && result.size() == 1, "Fallback should run once after recursive original");
        IllegalStateException getterFailure = new IllegalStateException("broken getter");
        try {
            FeatureCycleResolver.withFallback(List.of("test"), ignored -> { throw getterFailure; },
                    () -> { throw new IllegalStateException("Feature order cycle found"); });
            throw new AssertionError("Resolver failure swallowed");
        } catch (IllegalStateException actual) {
            check(actual == getterFailure, "Resolver failure identity changed");
        }
    }

    private static void randomCycles() {
        Random random = new Random(0xC1C1E);
        for (int trial = 0; trial < 100; trial++) {
            List<List<HolderSet<PlacedFeature>>> biomes = new ArrayList<>();
            for (int biome = 0; biome < 8; biome++) {
                List<HolderSet<PlacedFeature>> steps = new ArrayList<>();
                for (int step = 0; step < 3; step++) {
                    List<Holder<PlacedFeature>> holders = new ArrayList<>();
                    for (int index = 0; index < 12; index++) {
                        if (random.nextBoolean()) holders.add(FEATURES.get(index));
                    }
                    Collections.shuffle(holders, random);
                    steps.add(HolderSet.direct(holders));
                }
                biomes.add(steps);
            }
            WARNINGS.clear();
            var result = FeatureCycleResolver.resolve(biomes, biome -> biome);
            membership(biomes, result);
            for (int step = 0; step < 3; step++) {
                boolean[][] retained = new boolean[12][12];
                boolean[][] removed = new boolean[12][12];
                for (List<HolderSet<PlacedFeature>> biome : biomes) {
                    HolderSet<PlacedFeature> holders = biome.get(step);
                    for (int index = 1; index < holders.size(); index++) {
                        int from = FEATURES.indexOf(holders.get(index - 1));
                        int to = FEATURES.indexOf(holders.get(index));
                        String pair = "step=" + step + ", removed=" + holders.get(index - 1).unwrapKey().orElseThrow().location()
                                + " -> " + holders.get(index).unwrapKey().orElseThrow().location() + ",";
                        if (WARNINGS.stream().anyMatch(message -> message.contains(pair))) {
                            removed[from][to] = true;
                        } else {
                            retained[from][to] = true;
                            var mapping = result.get(step).indexMapping();
                            check(mapping.applyAsInt(holders.get(index - 1).value()) < mapping.applyAsInt(holders.get(index).value()),
                                    "Surviving precedence edge was violated");
                        }
                    }
                }
                // Independent transitive closure verifies each logged deletion is necessary.
                for (int via = 0; via < 12; via++) {
                    for (int from = 0; from < 12; from++) {
                        for (int to = 0; to < 12; to++) retained[from][to] |= retained[from][via] && retained[via][to];
                    }
                }
                for (int from = 0; from < 12; from++) {
                    check(!retained[from][from], "Retained graph still contains a cycle");
                    for (int to = 0; to < 12; to++) {
                        check(!removed[from][to] || retained[to][from], "A deletion could have been restored");
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--config")) {
            FMLPaths.loadAbsolutePaths(Path.of(".").toAbsolutePath());
            Path config = FMLPaths.CONFIGDIR.get().resolve("solace-performance.json");
            String json = new String(java.util.Base64.getDecoder().decode(args[1]), java.nio.charset.StandardCharsets.UTF_8);
            Files.writeString(config, json);
            boolean expected = Boolean.parseBoolean(args[2]);
            check(CommonPerformancePatches.featureCycleResolver() == expected, "Wrong startup gate");
            check(new SolaceMixinPlugin().shouldApplyMixin("net.minecraft.world.level.biome.FeatureSorter",
                    "com.solace.mixin.FeatureSorterCycleMixin") == expected, "Mixin plugin ignored gate");
            Files.writeString(config, "{\"featureCycleResolver\":" + !expected + "}");
            check(CommonPerformancePatches.featureCycleResolver() == expected, "Config must be startup-only");
            System.out.println("PASS feature-cycle startup gate " + json);
            return;
        }
        SharedConstants.tryDetectVersion();
        // Match the existing noise-hash harness. Fixtures need static codecs, without Forge startup.
        var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        MappedRegistry<PlacedFeature> registry = new MappedRegistry<>(Registries.PLACED_FEATURE, Lifecycle.stable());
        for (int index = 0; index < 20; index++) {
            String name = switch (index) {
                case 0 -> "minecraft:vines";
                case 1 -> "minecraft:seagrass_river";
                default -> "test:feature_" + index;
            };
            var configured = new ConfiguredFeature<>(Feature.NO_OP, FeatureConfiguration.NONE);
            PlacedFeature placed = new PlacedFeature(Holder.direct(configured), List.of(CountPlacement.of(index + 1)));
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, new ResourceLocation(name));
            registry.register(key, placed, Lifecycle.stable());
            FEATURES.add(registry.getHolderOrThrow(key));
        }
        Logger logger = (Logger) LogManager.getLogger(FeatureCycleResolver.class);
        AbstractAppender capture = new AbstractAppender("cycle-checks", null, null, false, null) {
            @Override public void append(LogEvent event) {
                if (event.getLevel() == org.apache.logging.log4j.Level.WARN) WARNINGS.add(event.getMessage().getFormattedMessage());
            }
        };
        capture.start();
        logger.addAppender(capture);
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        try {
            acyclic();
            cyclic();
            randomCycles();
            failures();
        } finally {
            logger.removeAppender(capture);
            logger.setAdditive(additive);
            capture.stop();
        }
        System.out.println("PASS feature-cycle resolver: 500 real-vanilla DAG comparisons, 100 random cyclic graphs with necessary-deletion checks, exact passthrough, equal feature identities, weighted SCC repair, self-loops, sparse steps, deterministic repeats, 32 concurrent calls, diagnostics and exception propagation");
    }
}
