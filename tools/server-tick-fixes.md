# Server tick fixes

These patches use `solace-performance.json` (`PerformanceConfig`). Each switch defaults
to `true`, respects the global `enabled` switch, and requires a restart. The exact
common patches apply on integrated and dedicated servers independently of
`singleplayerOnly`:

| Key | Behavior |
| --- | --- |
| `earthWaterSectionScan` | Filter Earth & Water's chunk-load scan by non-air sections and section palettes. |
| `fluidGameRuleCache` | Cache the live water/lava source-conversion value objects per `GameRules` instance. |
| `instrumentModPresenceCache` | Use static final Alex's Mobs/Immersive Melodies presence flags in cockroach band support. |

## Earth & Water

Inspected both `earth-and-water-1.0.3-1.20.1-hotfix2.jar` and Connector's existing
`.connector/earth-and-water-1.0.3-1.20.1-hotfix2_mapped_srg_1.20.1.jar` read-only.
The original private method has intermediary arguments `class_3218`, `class_2818`.
The cached runtime method is:

```text
scheduleBlockTicksForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V
```

Its name stays unchanged; vanilla calls become SRG names, including
`LevelChunk.m_8055_` (getBlockState) and `ServerLevel.m_186460_` (scheduleTick).
The mixin therefore uses that explicit descriptor with `remap = false`, `@Pseudo`,
`require = 0`, and a cancellable HEAD injection. The plugin also checks target-class
presence. The helper resolves all five registry IDs lazily after registration;
if any resolves to null/air it leaves the original method intact for that launch.
The IDs and static target fields were checked against `ModBlocks` bytecode.

Candidate sections are collected in ascending height order. Only these sections
are read block by block, retaining the original x/z/y tick insertion order,
world height bounds, immutable positions, block identities, and delay of 2.
Conservative/stale palette matches merely cause extra scanning.

## Fluid rules and compatibility

The redirect replaces only the `getRule` invocation inside `GameRules.getBoolean`.
Water and lava keys cache a `BooleanValue` reference on that instance; all other
keys still call `getRule`. Lazy initialization also covers the map constructor
used by `copy()`. Commands, tag loading, and `assignFrom` mutate existing values;
the cached references therefore stay live. No boolean result, level, or global
`GameRules` instance is cached.

Forge 47.4.15's `FlowingFluid.getNewLiquid` calls the Forge source-creation event
through `FluidState.canConvertToSource`; the default flowing-fluid implementation
delegates to water/lava's `canConvertToSource(Level)`. Both use `getBoolean`, so
the shared redirect covers both without bypassing the event or fluid overrides.

Inspected installed mixin class metadata/bytecode, including Connector's cached
mods. Streams Reflowing 2.17.1 wraps `Fluid.isSame` in `getNewLiquid`, injects
`sourceOnlyWhenEnclosed` at its RETURN, and injects water `isSame` at HEAD. It
does not target source conversion or the rule lookup. Radium's gamerules mixin
replaces the map at constructor RETURN; KubeJS adds rule helpers. Neither targets
the redirected call. Redirected's lava mixin targets `hasFlammableNeighbours`.
No installed mixin was found redirecting `getRule` inside `getBoolean`.

## Verification and limits

- Java 17 Gradle build, including reobfuscation and packaging.
- Generated refmap maps `getBoolean` to `m_46207_` and the redirected `getRule`
  to `m_46170_`; the optional Earth & Water descriptor remains unremapped.
- An offline harness invoked the production cache handler against actual Forge
  `GameRules`: 20,000 reads used two lookups, cached identities observed mutations,
  copies stayed independent, `assignFrom` and tag loading updated the cached value,
  and unrelated keys retained normal lookups.
- An offline harness compiled the production scan helper against lightweight
  world/registry stubs. Across 120 seeded chunks it matched the original ordered
  tick list with negative/custom heights, clipped world bounds, section and chunk
  edges, all five targets, air/target-free sections, and stale/global palettes.
  A separate process verified missing-target fallback without partial scheduling.

Minecraft was not launched. Actual mixin application in the full pack and CPU
savings still require a later authorized runtime/profile check. An updated Earth
& Water method may quietly retain its original implementation (`require = 0`).
Replacing source-conversion value objects at runtime, rather than mutating them,
would invalidate cached references; no such installed behavior was found.
The mod-presence flags assume Forge's mod list is fixed when gameplay events first
load the support class, as in the current event registration.
