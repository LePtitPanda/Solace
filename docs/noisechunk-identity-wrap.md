# NoiseChunk equality-preserving hash memo (Minecraft 1.20.1)

## Replacement and configuration

`NoiseChunkHashMemoMixin` replaces the former identity-map mixin. The old map
changed vanilla sharing and caused millions of duplicate cache wrappers in play.
It has been removed, together with the `noiseChunkIdentityWrap` setting and JVM
property. Old JSON files containing that field remain readable. Gson ignores the
obsolete field and the existing config writer removes it on its next normal save.

The new startup-only `noiseChunkHashMemo` setting defaults to **true** in both
`PerformanceConfig.ConfigData` and the early `CommonPerformancePatches` reader.
It is independent of `singleplayerOnly`. Either of these JVM properties disables
it, as do `noiseChunkHashMemo=false` or the common `enabled=false` JSON setting.

* `-Dsolace.commonPerformance.noiseChunkHashMemo=false`
* `-Dsolace.commonPerformance=false`

Restart after changing the setting. An old `noiseChunkIdentityWrap=false` does
not disable the replacement. Missing files and omitted new fields use the new
true default. The early reader does not load game classes or write configuration.
Unreadable or malformed configuration disables common performance patches.

## Verified 1.20.1 code and injection point

Re-extracted the actual class files from ForgeGradle's cached
`forge-1.20.1-47.4.15_mapped_official_1.20.1.jar` and decompiled them with
Vineflower 1.10.1. Cross-checked the cached sources and Java 17 `javap` output.
Inspection artifacts stay under `build/noise-hash/inspect`.

| Mojmap | SRG | Behavior |
| --- | --- | --- |
| `NoiseChunk.wrapped` | `f_209161_` | Private final equality-keyed map, initially a `HashMap`. |
| `NoiseChunk.wrap` | `m_209213_` | `computeIfAbsent(function, this::wrapNew)`. |
| `NoiseChunk.wrapNew` | `m_209233_` | Creates marker wrappers, substitutes chunk functions, unwraps holders, or returns the input. |
| `NoiseRouter.mapAll` | `m_224412_` | First traversal using `this::wrap` in the constructor. |

The injection remains immediately before that first `NoiseRouter.mapAll`, after
all field initializers and before the first `wrap`. It requires exactly one site.
This preserves compatibility with the previously inspected ModernFix 5.27.91
priority-100 allocation mixin, which initializes a fastutil map and changes wrap
to get / wrapNew / put. Both access paths work with the replacement. No methods
are overwritten. The built refmap maps `mapAll` to `m_224412_`, and the reobfuscated
mixin shadows `f_209161_`.

`wrapped` has no iteration consumers. Later final-density and climate traversals
also use the same map. All marker registration, interpolation updates, blending,
and cache lifetime behavior still belongs to `wrapNew` and the owning NoiseChunk.

## Map semantics and cost

`DensityFunctionHashMemo.newWrappingMap()` creates a chunk-local
`Object2ObjectOpenCustomHashMap`. Its strategy uses the function's original
`hashCode()`, memoized by object identity, and compares keys with
`a == b || (a != null && a.equals(b))`.

For stable, contract-compliant keys, the memoized integer always equals the
original hash. Equal-but-distinct keys therefore enter the same equivalence class
and reuse the first wrapper, including across collisions and table rehashes.
Inductively, the same postorder visitor calls return the same canonical child
wrappers, so the next parent keys retain the same equality relationships. The
map makes the same number of `wrapNew` calls as vanilla for the same traversal.

A cached identity hit avoids both structural hashing and equality. Distinct equal
keys still need their structural equality comparison to establish sharing. Cold
hashes still run the original recursive `hashCode`, including child hash calls.
The optimization does not intercept recursive calls within a record hash. There
is no equality-pair cache or per-chunk identity front map.

`RandomState` retains its router, but `mapAll` rebuilds many of the keys that
actually reach `NoiseChunk.wrap`. `MarkerOrMarked` maps its child and creates a
new Marker. `HolderHolder` creates a new direct holder around its mapped child.
Arithmetic and spline traversals also reconstruct nodes. Once child caches have
been substituted, parents can contain chunk-specific wrappers. Consequently,
cross-chunk identity reuse mainly helps retained leaves and other unchanged
nodes. The memo also avoids repeat hashing during ModernFix get/put, subsequent
lookups, and rehashes within a chunk. CPU improvement must be measured in play.

## Hash stability

The inspected vanilla key graph has stable equality and hashes during wrapping
and sampling. Java records alone only guarantee final component references.
The relevant component implementations were also checked.

* Marker, arithmetic, range, clamp, gradient, noise, and holder records retain
  their components. Their generated hashes are structural. `visitNoise` creates
  replacement NoiseHolder records rather than mutating them.
* `Holder.Direct` hashes its final value. `Holder.Reference` uses object identity,
  so registry binding does not change its hash.
* `BlendedNoise`, `NormalNoise`, EndIslandDensityFunction, Beardifier, and the
  chunk cache/interpolator classes inherit identity equality and hashing.
  Mutable sample arrays, iterator positions, and interpolation counters do not
  participate in hashes. `BlendedNoise.withNewRandom` creates a new instance.
* Vanilla spline multipoints have fixed component references and value lists.
  Array components use array identity in record equality/hashing. Vanilla does
  not mutate spline value lists while evaluating a router.

There are mutable density-function *objects*, including cache wrappers. The
relevant property is stability of hash/equality, which their mutable sampling
state preserves. This inspection does not establish compatibility with every
possible third-party implementation that mutates hash/equality between maps.

## Streams Reflowing sampler inspection

Inspected a fresh download of the manifest-listed CurseForge file 9486907,
`StreamsReflowing-1.20.1-forge-2.17.1.jar`, inside this worktree. Its SHA-1 matched
the instance manifest, `d3c9775f7b53d4c51cf14d1154f30ab9aeb30a86`.
The instance mods/config directories were not modified.

The snapshot sampler decoder constructs its own generator, frozen registry view,
and RandomState, then retains that state in the sampler object. Repeated height
queries pass that same RandomState to `NoiseBasedChunkGenerator.getBaseHeight`.
Vanilla `iterateNoiseColumn` creates `new NoiseChunk(1, randomState, ...)`, so this
mixin covers that path as well as normal chunks. Another batch path uses
`NoiseChunk.forChunk` with the level's RandomState. Direct evaluators also read
router density functions and may avoid NoiseChunk construction altogether.

The snapshot router is separate from the live world's router, so identity reuse
is scoped to objects actually shared by each sampler/state. It cannot assume
shared object identities across those independently decoded routers. Inspected
Streams SimpleFunction cache wrappers contain mutable sampling caches but have
no equals/hashCode overrides, preserving stable object-identity hashing.
No Streams implementation is copied into Solace.

## Concurrency and memory bound

The global memo is Minecraft's existing Guava `Cache`, with `weakKeys()` and
`maximumSize(65_536)`. Guava weak keys use identity comparisons. Values contain
only boxed integers. No cached value, loader, callback, or strategy closes over
a density function, NoiseChunk, world, or router.

Cache lookups, insertions, eviction, and reference-queue maintenance are
thread-safe. Hash computation happens outside cache locks. Concurrent cold
misses may recompute the same stable hash and safely publish the same integer.
Each worker/sampler still owns its ordinary unsynchronized wrapping map, matching
NoiseChunk's existing confinement requirements. This does not make a single
NoiseChunk safe for simultaneous mutation.

Weak keys permit chunk-specific trees and retired routers to be collected.
The size policy bounds entries even when all keys are live, with normal Guava
maintenance/concurrent-operation transients. Stale weak-reference metadata and
integers can remain until cache maintenance runs, within that size policy.
Eviction only causes another original hash computation on the next lookup.
The limit is a fixed engineering choice, independent of chunk count. It adds
bounded cache bookkeeping and does not promise a particular hit rate.

## Offline verification

Run `JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew build --console=plain -q`
(or set `$env:JAVA_HOME` first in PowerShell). `check` runs the standalone
`verifyNoiseHashMemo` task. It can also be run separately.

The verifier uses the real mapped DensityFunction, DensityFunctions factories,
and mapAll implementations. It sets Bootstrap's guard flag in the isolated test
JVM to permit static codec initialization without invoking Forge bootstrap.
It does not decode registry/world data or start a Minecraft client or server.

Passed under Java 17 with Minecraft's fastutil and Guava dependencies.

* Both vanilla computeIfAbsent and ModernFix get/put produce 4,096 wrappers from
  8,192 equal-but-distinct nested density trees, matching HashMap through rehashes.
* Repeated real mapAll traversals produce the same 128 test marker wrappers and
  map sizes as HashMap. The verifier substitutes lightweight test wrappers at
  marker visits, exercising canonical-child sharing without allocating chunks.
* Deliberate equal-hash unequal keys stay distinct. Repeated identity hits on a
  nested record tree make zero hashCode/equals calls to its instrumented leaf.
* Eight workers use independent maps and one shared global memo, preserving all
  identity and equal-tree associations.
* With 80,000 strongly reachable keys, cache maintenance keeps at most 65,536
  entries. Evicting a retained key's hash preserves its existing wrapper
  association. A discarded temporary record tree is collected while the memo lives.

Separate isolated startup-gate checks passed for missing files, omitted keys,
obsolete false keys, explicit new/global disables, singleplayerOnly, malformed
JSON, and both JVM overrides. ConfigData serialization preserves the new true
default and omits the obsolete field. Their fixtures remain under build.

The full requested build passed. Existing unrelated deprecation warnings remain.
Full-pack Mixin application, actual world output, and CPU/heap improvement remain
in-game validation items.

## In-game comparison

Compare the new switch off and on in separate restarted sessions. Use the same
pack, seed, dimension, exploration route, render/simulation distances, sampler
workload, and comparable loaded-chunk counts. Include both stream preparation
and ordinary exploration. Allow old preparation work to finish before comparing
retained state.

1. After a forced full GC, compare live NoiseChunk counts and FlatCache,
   Cache2D, Marker, CacheOnce, CacheAllInCell, NoiseInterpolator, and double[]
   counts/bytes. Normalize wrapper counts by live NoiseChunks. Different sample
   and regular chunk sizes can change that ratio, so also compare workloads.
   Counts should track the equality-map baseline, without the identity-map
   explosion of roughly 2,800 FlatCaches per NoiseChunk.
2. Inspect retained paths if NoiseChunks or arrays remain unexpectedly high.
   The static memo should reach function keys only through weak references.
   Compare total live heap and bounded Guava memo overhead across long runs and
   world unload/reload as well as the initial exploration period.
3. Record equivalent sampler-thread and C2ME-worker JFR profiles after warmup.
   Compare absolute CPU time, throughput, allocation/GC rate, record hashing,
   record equality, and Guava cache work. Structural equality between distinct
   keys remains. A change in sampling phase can distort percentage-only results.
4. Confirm the startup mixin applies with ModernFix/C2ME and compare deterministic
   chunk/height outputs between the two settings. The offline tests exercise map
   semantics and real traversal, with the full pack's runtime integration left to
   this comparison.
