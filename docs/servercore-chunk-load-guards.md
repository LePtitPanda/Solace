# ServerCore chunk-load guards for Solace

## Source and licence

Fetched and scanned all Java sources of [ServerCore's `1.20.1` branch](https://github.com/Wesley1808/ServerCore/tree/d1d0a02d39d0739441419e3a20f46fbc88d98ec5),
commit `d1d0a02d39d0739441419e3a20f46fbc88d98ec5` (including its common and Forge source sets).
Read every candidate guard and its helper implementation. The inventory below is scoped to this
revision, rather than newer Minecraft branches.

The [README licence section](https://github.com/Wesley1808/ServerCore/blob/d1d0a02d39d0739441419e3a20f46fbc88d98ec5/README.md#license)
assigns MIT to files without an explicit GPL-3.0 header. Read `licenses/MIT.md` and the explicit GPL header
on the movement patch. All five ports are from MIT files. Each mixin credits Wesley1808/ServerCore and
the source revision; the unchanged upstream MIT notice is packaged at
`META-INF/licenses/servercore-MIT.txt`. No GPL movement code or its bounding-box helper was copied.

## Complete inventory and decisions

Paths in this table are relative to
`common/src/main/java/me/wesley1808/servercore/mixin/` in the pinned source.
The eight `optimizations/sync_loads` files form ServerCore's `reduce_sync_loads` option.
The remaining rows cover related load/ticket avoidance found outside that directory.

| Upstream file | Guard / effect | Forge 1.20.1 decision |
| --- | --- | --- |
| `optimizations/sync_loads/GroundPathNavigationMixin.java` | Reject an unloaded target before the ground navigator scans its vertical block column. Returns no path so AI can retry. | **Ported.** High value for distant AI targets; preserves ordinary loaded navigation. This only guards the target-column pre-scan, not all pathfinding or flying/swimming navigators. |
| `optimizations/sync_loads/SleepInBedMixin.java` | Reject a sleeping task if the remembered HOME bed's chunk is unavailable, before reading its block state. | **Ported.** Retains the HOME memory and permits later retries. Solace reads the same memory at HEAD instead of capturing local variables. This is villager/AI sleep, not player respawning. |
| `optimizations/sync_loads/MapItemMixin.java` | Replace forced map sampling loads with a nonblocking lookup; treat missing chunks as empty so those pixels are skipped. | **Ported.** Valuable when map sampling extends past loaded terrain. Existing pixels remain unchanged; mapping radius can be smaller than vanilla until terrain loads. Both redirects are required together. |
| `optimizations/sync_loads/RemoveBlockGoalMixin.java` | Use a nonblocking lookup when checking block-breaking targets (notably zombies searching for turtle eggs). | **Ported.** Existing null branch already rejects the target. Retains Forge's `canEntityDestroy` check and mob-griefing behavior. |
| `optimizations/sync_loads/DynamicGameEventListenerMixin.java` | Register/unregister dynamic listeners only in immediately available chunks, avoiding waits during listener movement/removal. | **Ported.** Vanilla already handles null; avoids reentrant waits during lifecycle work. Listener behavior at load boundaries still needs runtime testing. |
| `optimizations/sync_loads/BeeMixin.java` | Treat an unavailable hive like an invalid/distant hive during `Bee.isHiveValid`. | **Skipped.** Applicable, but the caller clears `hivePos` on failure. Temporarily unloaded hives could be forgotten. This branch has no separate general bee hive-search/pathing guard. |
| `optimizations/sync_loads/ServerLevelMixin.java` | Override ray tracing: return a miss when the ray's endpoint chunk is unavailable. | **Skipped.** Broad gameplay effects for visibility, combat and modded rays; endpoint-only checking also leaves intervening missing chunks unguarded. |
| `optimizations/sync_loads/StructureCheckMixin.java` | Reject buried-treasure candidates using an approximate biome at sea level before storage lookup. | **Skipped.** Approximation can reject valid candidates, especially in modded worldgen. ModernFix also hooks `checkStart` / `tryLoadFromStorage`; avoid stacking structure-location heuristics. |
| `optimizations/tickets/ChunkGeneratorMixin.java` | For `getMobsAt`, use a loaded chunk's structure references instead of fetching them through `StructureManager`. Falls back to the original lookup when missing. | **Skipped.** Primarily avoids unnecessary tickets rather than guaranteeing no loads. Lower value than the five ports and touches structure-based spawning, already modified by Forge and other optimizers. |
| `optimizations/tickets/NaturalSpawnerMixin.java` | Read nether-fortress floor blocks from a loaded chunk, using air if unavailable, to avoid lookup tickets. | **Skipped.** Applicable to Forge's `isInNetherFortressBounds`, but lower value and interacts with fortress spawn decisions. C2ME/Radium both patch spawning infrastructure; preserve those semantics in this port. |
| `optimizations/biome_lookups/NaturalSpawnerMixin.java` | Read a rough chunk noise biome, with an uncached noise-biome fallback when absent, instead of the normal biome lookup. | **Skipped.** Upstream defaults it off: biome-border spawn results can change. It is a biome/spawning approximation as well as a way to avoid chunk lookup. |
| `features/misc/EntityMixin.java` | During dimension entry, skip the destination spawn-heightmap read if the spawn chunk is absent; retain the default spawn position. | **Skipped.** Changes arrival height/safety; ModernFix's `remove_spawn_chunks.EntityMixin` also hooks `findDimensionEntryPoint`. No general player bed/anchor respawn guard exists in this revision. |
| `features/misc/ServerGamePacketListenerImplMixin.java` | Reject player and vehicle movement into unavailable chunks using the swept bounding box; teleport/correct back. | **Skipped.** Explicit **GPL-3.0** source is outside the permitted licences. Also risks rubber-banding during exploration; upstream defaults the feature off. This is packet movement, not a universal entity-movement patch. |
| `features/ticking/VillagerMixin.java` | The optional villager lobotomization feature uses nonblocking chunk reads for its local travel/collision probes. | **Skipped.** These are safety checks for a new ticking feature, not replacements for vanilla synchronous lookups. Porting them would require the unwanted villager brain throttling feature. |

`ChunkManager.java` supplies the shared loaded-chunk, fallback block/biome, and movement helpers.
Cached ticking-chunk iteration uses its completed-future helper too; this is a cache, not another vanilla
lookup guard. Spawn-chunk ticket removal (`Events` and `features/spawn_chunks/PlayerListMixin`) is a
separate world-lifecycle feature, also outside this port. No dedicated general POI load guard was found
in this revision. Solace already filters unloaded POI candidates in `PoiManagerMixin` /
`ServerWorkScheduler`; Radium also replaces POI queries, so no additional POI mixin was added.

## Implementation and configuration

Added common-side mixins under `com.solace.mixin.chunkguards`:

- `GroundPathNavigationChunkGuardMixin`
- `SleepInBedChunkGuardMixin`
- `MapItemChunkGuardMixin`
- `RemoveBlockGoalChunkGuardMixin`
- `DynamicGameEventChunkGuardMixin`

All five share `chunkLoadGuards` in the existing `solace-performance.json` configuration.
The default is `true`: the chosen lookups either defer retryable work or already accept a missing
chunk. `enabled` and `singleplayerOnly` retain their existing meaning. With the default
`singleplayerOnly: true`, the guards run on integrated servers; dedicated servers require
`singleplayerOnly: false`. Clients and world-generation readers keep their original lookup behavior.

To disable the group, add/set `"chunkLoadGuards": false` and restart, or use the existing configuration
reload path. Existing JSON files can omit the new key, in which case it defaults to true; the existing
config loader does not automatically rewrite every existing file to add new keys.

`SolaceMixinPlugin.shouldApplyMixin` skips this entire group if ServerCore's upstream map-guard class
is present, preventing duplicate redirects if ServerCore is installed later. This defers to ServerCore
even if its own optimization option is disabled.

The main adaptation is to use Forge's public `ServerChunkCache.getChunkNow` rather than porting
ServerCore's private holder/future access machinery. Inspected Forge 47.4.15's implementation: it
checks the current thread and cache, then uses an immediate future lookup (`getNow`), with no join,
managed wait, or ticket creation. It preserves Forge's `currentlyLoading` deadlock workaround.
In contrast, `getChunk(..., FULL, false)` can still wait for work associated with an existing ticket.
No chunk-source overwrite or new optimization-mod compile dependency was added.

## Conflict checks

Read the installed jars only. Inspected mixin configurations, ASM class target/injector annotations,
and relevant method bytecode, including nested jars. Fabric target IDs were resolved using the
Yarn `1.20.1+build.10` mappings (e.g. `class_1409` = ground navigation, `class_4123` = sleep,
`class_1806` = filled map, `class_1382` = remove-block goal, `class_5715` = dynamic listener).

| Installed jar | Checks and findings |
| --- | --- |
| `c2me-fabric-mc1.20.1-0.2.0+alpha.11.20.26.9.jar` | Recursively inspected its bundled modules. No mixin targets any of the five ported classes. Reviewed chunk-access `async_chunk_request.MixinServerChunkManager` (`getChunk` HEAD), base priority hooks (blocking `getChunk` wait), no-tick view distance, general-threading checks, and scheduling hooks. These alter underlying loading/ticking, not the new call-site guards. Also identified structure-checker and spawn-helper targets when evaluating skipped candidates. |
| `radium-mc1.20.1-0.12.4+git.26c9d8e.jar` | No mixin targets any of the five classes. `entity.inactive_navigations.EntityNavigationMixin` targets parent `PathNavigation` recalculation/start/stop; `ai.pathing.ChunkCacheMixin` and `util.chunk_access.ChunkCacheMixin` target `PathNavigationRegion`. `world.chunk_access.ServerChunkManagerMixin` changes cached `getChunk` access and cache invalidation, not `getChunkNow`. `ai.poi.PointOfInterestStorageMixin` and its `fast_portals` variant overwrite POI queries. Also reviewed spawning-related target inventory. |
| `modernfix-forge-5.27.91+mc1.20.1.jar` | No mixin targets any of the five classes. `bugfix.chunk_deadlock.ServerChunkCache_CurrentLoadingMixin` injects into `getChunk`; `ChunkMapLoadMixin` manages load futures; `bugfix.chunk_deadlock.EntityMixin` guards passenger game events. These are adjacent fixes. `perf.faster_structure_location.StructureCheckMixin` modifies the `tryLoadFromStorage` result in `checkStart`; `perf.remove_spawn_chunks.EntityMixin` hooks dimension entry. These informed the skipped structure/arrival guards. |

Expanded the target scan to all **483 root `.jar` files** currently in the mods directory, recursively
including their nested jars. This is a conservative inventory of class annotations, not a claim that
every discovered mixin is enabled at runtime. Additional relevant targets:

- Liberty Villagers `SleepTaskMixin`: changes the reachable-bed distance constant in the same sleep
  method. Solace's HEAD guard preserves that constant change for loaded beds; there is no redirect clash.
- Icy's Better Horses `MapItemMixin`: changes the map scan-radius constant and adds an inventory-tick
  hook. Solace's per-sample loaded check remains applicable to that expanded scan.
- Moonlight `MapItemMixin`: HEAD callback can cancel vanilla updates for custom map data. Inspected
  its bytecode; Solace leaves that callback in place and only guards vanilla sampling that continues.
- Supplementaries `MapItemMixin`: ceiling, height-lock, height, color and update-color hooks. None
  redirects either the chunk lookup or `isEmpty` call. Missing chunks skip the original guarded body,
  including those height/color operations; loaded samples retain the mod hooks.
- Async Locator and Integrated API: map accessors/invokers only.
- Bee targets in Friends & Foes, Golem Overhaul, Immersive Weathering, Liberty Villagers and Wither
  Storm were also inventoried; the bee guard was skipped.

No installed same-call redirect conflict was found for the selected guards. Static jar inspection
cannot prove the final transformed Connector/Forge runtime is compatible, or cover dynamically
generated transformations.

## Verification and remaining runtime tests

- `JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew build --console=plain -q` succeeded (exit 0).
  Existing deprecation and mixin-target-style warnings remain; no warnings referenced the new guards.
- Offline ASM inspection of Forge 1.20.1-47.4.15 bytecode resolved all **six injectors** uniquely:
  two exact-method HEAD injections and four invocation redirects (the map uses two).
- Inspected the final `build/libs/solace-1.0.jar`: all five mixins, runtime gate, common mixin entries,
  upstream licence, and SRG mappings for every new selector/invocation are present.
- `git diff --check` passed. Minecraft was not launched and the jar was not installed into the instance.

In-game validation still required with the actual pack on Java 21:

1. Confirm mixin application and clean startup in both the integrated server and a dedicated server
   with `singleplayerOnly: false`. Check C2ME/Connector/Radium/ModernFix together.
2. Compare `chunkLoadGuards` true/false in repeatable boundary scenes, profiling server-thread chunk
   waits and tick spikes. No measured speedup is claimed from a build or static inspection.
3. Map all zoom levels near loaded-terrain boundaries, then load the missing terrain and confirm
   pixels fill in. Exercise Supplementaries height-locked/custom maps and Better Horses scanning.
   Custom map implementations that replace vanilla sampling can bypass this guard.
4. Pathfind ground mobs toward unavailable targets, then load the targets and check recovery,
   doors, terrain and existing farms. This is not a blanket no-load guarantee for pathfinding regions.
5. Exercise villager beds/HOME memories across load boundaries and Liberty Villagers' longer sleep
   reach; verify sleeping resumes and HOME is retained. Player bed/anchor respawns remain unpatched.
6. Test zombie turtle-egg searches with mob griefing on/off, Forge destruction restrictions, loaded
   and unloaded targets, and successful reacquisition after loading.
7. Move/remove dynamic listeners (wardens, allays and relevant modded entities) near chunk unloads;
   verify vibration registration/removal and subsequent recovery without hangs or missed events.
