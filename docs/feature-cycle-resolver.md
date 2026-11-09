# Feature-order cycle recovery

The implementation is independent. The idea is similar to Feature Recycler. No code from that project was used.

## Entry point and configuration

The mapped Forge 1.20.1 / 47.4.15 JAR and its source JAR confirm the public static generic method `FeatureSorter.buildFeaturesPerStep(List<T>, Function<T, List<HolderSet<PlacedFeature>>>, boolean)`. Its JVM descriptor is `(Ljava/util/List;Ljava/util/function/Function;Z)Ljava/util/List;` and its SRG name is `m_220603_`. The generated refmap contains that exact mapping.

`FeatureSorterCycleMixin` uses MixinExtras `@WrapMethod`. It calls the original operation first and returns the same result object immediately on success. Only an `IllegalStateException` with a message beginning with `Feature order cycle found` invokes recovery. Other exceptions propagate unchanged. A thread-local guard lets vanilla's recursive source-witness reduction complete before recovery. The guard is removed even on failure. All graph state is invocation-local.

The startup-only common switch `featureCycleResolver` defaults to `true` in `solace-performance.json`. The existing `enabled` switch and the `solace.commonPerformance` and `solace.commonPerformance.featureCycleResolver` system properties also gate mixin application. `singleplayerOnly` does not restrict this common-side repair. Restart after changing the JSON switch. Older JSON files that omit it use the default.

The distributable `build/libs/solace-1.0.jar` includes MixinExtras Forge 0.4.1 through Forge Jar-in-Jar. Both the regular slim JAR and bundled distributable are reobfuscated. The normal Gradle `build` produces the bundled distributable.

## Graph and repair policy

Recovery reconstructs vanilla's first-seen feature indices, feature equality, step/index node comparator, and consecutive edges through each source's flattened feature lists. This retains forward step-boundary edges and empty/trailing step slots. Each edge records distinct contributing source indices in input order. Inline features use their encounter index for diagnostic names.

Tarjan's algorithm identifies strongly connected components. Because step-boundary edges only advance to higher steps, every cycle belongs to one decoration step. Each iteration removes the cyclic edge with the fewest contributing biomes. Ties select the earliest contributing biome, then the source and destination nodes in vanilla step/first-seen feature order. No identity hash codes or hash-map traversal order affect the decision.

After every removal, SCCs are recomputed. Once acyclic, deletions are reconsidered in reverse order and restored when they cannot create a cycle. The final set is inclusion-minimal. Computing a globally minimum weighted feedback-edge set is outside this heuristic's guarantee.

The repaired graph uses vanilla's ordered DFS postorder followed by reversal, including its step-boundary edges. All surviving precedence constraints remain satisfied. Contradictory constraints necessarily change feature order, and unrelated unconstrained nodes can shift as DFS reachability changes. Successful vanilla calls retain their original ordering and index mappings exactly.

Results are an immutable outer list of `FeatureSorter.StepFeatureData` records, each containing its step's feature list and vanilla's identity-based index lookup with the same missing-key behavior. ChunkGenerator consumes these records through its existing interface.

## Diagnostics

Each final removed edge emits one WARN with this exact message template.

```text
[Solace] Resolved feature order cycle: step={}, removed={} -> {}, overriddenBiomes={}
```

The step is zero-based. Feature and biome holders use registry IDs. Direct feature holders use `inline#<first-seen-index>`. Direct biome holders use `inline-biome#<source-index>`. Generic string sources print their names, and other generic sources use `source#<source-index>`. Each biome that directly contributed the removed edge appears once, in input order. Repeated invocations can log the conflict again for separate generator initializations.

An example message follows.

```text
[Solace] Resolved feature order cycle: step=9, removed=minecraft:vines -> minecraft:seagrass_river, overriddenBiomes=[blooming_biosphere:rainforest]
```

## Offline verification

Run `./gradlew build --console=plain -q` with `JAVA_HOME=C:/Program Files/Java/jdk-17`. The `check` lifecycle includes `verifyFeatureCycleResolver`, six startup gate fixtures, and the existing noise-hash checks. Individual resolver checks can be run with `./gradlew verifyFeatureCycleResolver --console=plain -q`.

The resolver harness uses the real mapped sorter on the runtime classpath. It compares 500 seeded acyclic synthetic biome sets feature-by-feature and index-by-index, checks exact original-result passthrough and equal-but-distinct feature identities, reproduces the vines/seagrass cycle in step 9, and checks weighted deletion, source ties, duplicate contributors, self-loops, multiple SCCs, sparse steps, feature membership, deterministic repeats, and 32 concurrent calls. A further 100 randomized cyclic graphs verify every retained constraint and independently check that every final deletion is necessary using transitive closure. It also verifies WARN text and unchanged propagation of unrelated exceptions, resolver failures, and recursive witness failures. Startup fixtures check defaults, explicit disable, global disable, common-side application independent of `singleplayerOnly`, both property overrides, plugin gating, and startup-only JSON reads. The artifact check inspects the final bundled JAR's SRG refmap, method-wrapper annotation, mixin registration, manifest, nested runtime, and reobfuscated game calls.

Fixtures permit static codec initialization using the existing standalone noise-hash harness's bootstrap-flag technique. They do not start a game client, server, or world. Live mixin application and full-pack world generation remain untested.
