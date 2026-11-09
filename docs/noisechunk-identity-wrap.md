# NoiseChunk identity wrapping (Minecraft 1.20.1)

## Verified code and mappings

Inspected ForgeGradle's `forge-1.20.1-47.4.15_mapped_official_1.20.1-sources.jar`
under `~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/`,
and cross-checked the mapped class with Java 17 `javap -p -c`.
Source references below are to `net/minecraft/world/level/levelgen/` inside that JAR.
The SRG names were checked in ForgeGradle's
`de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/srg_to_official_1.20.1.tsrg`.

| Mojmap | SRG | Vanilla use |
| --- | --- | --- |
| `NoiseChunk.wrapped` | `f_209161_` | Private final `Map<DensityFunction, DensityFunction>`, initialized with `HashMap` (NoiseChunk.java:34). |
| `NoiseChunk.wrap` | `m_209213_` | Only map reader/writer: `computeIfAbsent(function, this::wrapNew)` (326-328). |
| `NoiseChunk.wrapNew` | `m_209233_` | Builds marker wrappers, substitutes chunk-owned functions, unwraps holders, otherwise returns its argument (330-373). |
| `NoiseRouter.mapAll` | `m_224412_` | First constructor traversal that calls `wrap` (NoiseChunk.java:119-120). |

The map is never iterated; it is memoization, not a registry of lifecycle participants.
The constructor also maps the final density graph, and `cachedClimateSampler` maps
climate functions later. Replacing the map at constructor return would miss the
expensive construction work and discard already established sharing.

## Why separate equal functions preserve vanilla values

`DensityFunctions.HolderHolder.mapAll` recursively maps the held value and builds
a new direct holder (DensityFunctions.java:549-560). `MarkerOrMarked.mapAll`
recursively maps its child **before** passing a new marker to the visitor
(705-717). Thus distinct structurally equal records really can reach `wrap`.
Identity keys intentionally stop coalescing those records; this is a change in
sharing, not in the density arithmetic.

For vanilla's deterministic functions and correctly placed cache markers:

* Non-marker functions keep the same computation. `wrapNew` either returns the
  argument, unwraps `HolderHolder`, or returns the same per-chunk blend/beardifier
  substitute. Identity keys do not affect those singleton substitutions.
* `FlatCache` samples the same quart-coordinate grid at construction; `Cache2D`
  caches the same X/Z-only function (NoiseChunk.java:443-477, 566-608). Duplicating
  either cache changes work/storage, not a returned sample.
* `CacheOnce` stores values by the owning chunk's point/array counters and falls
  back to its child for other contexts (515-564). Counters only identify sampling
  epochs; their numeric value does not enter density arithmetic. Separate caches
  can miss independently and recompute the same point/array.
* `CacheAllInCell` registers itself in `cellCaches` (479-513), and every entry is
  filled at `selectCellYZ` (251-266). Postorder mapping keeps child caches before
  parents, including duplicates. The same cell coordinates and array indexing
  are used for each copy.
* `NoiseInterpolator` registers itself in `interpolators` (622-659). `fillSlice`,
  `selectCellYZ`, `updateForY/X/Z`, and `swapSlices` update **every** registered
  instance (187-204, 251-287, 298-300). Equal copies therefore get identical
  corner samples and the same interpolation operations and fractions (661-713).

No random source is consumed by these wrappers, and adding copies does not alter
the order of arithmetic within a sample. The same object still reuses its entry.
Additional wrappers can increase allocation/sampling work; the benefit is removal
of recursive record hashing/equality on every lookup, insert, and rehash.
This reasoning does not promise equivalence for custom stateful density functions,
broken equality implementations, or markers used outside their vanilla contracts
(for example a Y-dependent function inside Cache2D).

## Installed-mod compatibility inspection

Read only the installed JARs, including C2ME's nested module JARs, mixin configs,
refmaps, class references, and decompiled relevant classes:

* **ModernFix 5.27.91+mc1.20.1:**
  `common.mixin.perf.worldgen_allocation.NoiseChunkMixin` (priority 100) initializes
  `f_209161_` with `Object2ObjectOpenHashMap` and overwrites `m_209213_` with
  `Map.get`, `wrapNew`, `Map.put`. This explains the supplied JFR's fastutil path;
  it is not vanilla's original `HashMap.computeIfAbsent`. Its related
  `MaterialRuleListMixin` and `optimize_surface_rules.SurfaceSystemMixin` do not
  replace the wrap map.
* **C2ME fabric 0.2.0+alpha.11.20.26.9:** scanned all bundled module JARs for
  NoiseChunk / intermediary `class_6568` references and noise/density mixins.
  Checked worldgen threading's `MixinNoiseChunkGenerator` executor redirects,
  worldgen threading-fix generator mixin, vanilla aquifer generator/sampler
  mixins, and allocs' `MixinChainedBlockSource` (MaterialRuleList). None replaces
  `NoiseChunk.wrap`, `wrapNew`, or `wrapped`.
* **Radium 0.12.4+git.26c9d8e:** checked `lithium.mixins.json`, refmap/class
  references, and `gen.cached_generator_settings.NoiseChunkGeneratorMixin`.
  That mixin caches the generator's sea level; no wrap/map patch was found.
* **FerriteCore 6.0.1 Forge:** scanned mixin configs/refmaps and class references;
  no NoiseChunk target or wrap/map patch was found.

Solace injects immediately before the constructor's `NoiseRouter.mapAll` call,
after field initialization and before the first wrap. This supersedes ModernFix's
initializer without competing with its overwrite or redirecting its get/put.
Vanilla's computeIfAbsent works with the same identity map. There is one required
injection site; unexpected constructor changes fail instead of silently leaving
the optimization inactive. No global map or extra cross-thread sharing is added.

## Switch and validation

`noiseChunkIdentityWrap` defaults to true in `solace-performance.json` and is read
once by `CommonPerformancePatches` through `SolaceMixinPlugin.shouldApplyMixin`.
The common `enabled` switch also applies; `singleplayerOnly` applies to the existing
server scheduler, not this startup mixin. Restart after changing the key. JVM
overrides `-Dsolace.commonPerformance=false` or
`-Dsolace.commonPerformance.noiseChunkIdentityWrap=false` disable it. The early
reader follows the existing client switch pattern, avoids loading Minecraft
classes, never writes configuration, and disables the patch on a read/parse error.
`PerformanceConfig.ConfigData` includes the key so its existing writer preserves it.

Validation: the requested online Java 17 Gradle build passed. Checked the packaged
SRG shadow field and refmap's `NoiseRouter.mapAll` remapping. An isolated Java 17
check using Minecraft's fastutil 8.5.9 inserted 16,384 distinct identity keys,
including structurally equal pairs, forced rehashes, and checked repeated hits for
both vanilla computeIfAbsent and ModernFix get/put: zero key hashCode/equals calls.
Isolated gate checks (with FMLPaths pointed at worktree fixtures) passed for a
missing file, omitted key, explicit key/global disables, both JVM overrides,
malformed JSON, and enabling common behavior with `singleplayerOnly: true`.

Minecraft was not launched. Actual Connector/Mixin application in the full pack,
seed/chunk output comparisons, memory/duplicate-cache growth, and the measured JFR
gain remain unverified. No instance mods/config files were changed.
