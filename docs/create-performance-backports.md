# Create 6.0.8 performance backport evaluation

Evaluated 2026-11-09 in `Solace-create-perf`, branch `codex/create-perf`.
Target: Forge 47.4.15 / Minecraft 1.20.1, installed `create-1.20.1-6.0.8.jar`.
Target SHA-256: `6fbb910c367dbce8e4fc7e5bf64b6edd4de980906ed00af8e47e4af843c0d9b0`.

Read all 176 commits returned for `mc1.21.1/dev` since 2025-11-01 (100 + 76;
next page empty). Branch head was `4148fd8d5b05c85d88947edc00fb495a5074f57d`.
Searched full commit messages case-insensitively for `perf|optimi|cache|lag|allocation`.
Fetched and read the ten substantive candidate diffs below. The other two matches
were `174671d` ("Perfectly Emissive", a rendering correctness fix matching `perf`
inside "Perfectly") and `52b8465` (merge message listing generated `.cache` files).

Relevant classes were extracted from the installed jar and decompiled with the
user-supplied CFR 0.152. Source/decompilation and downloaded upstream patches are
local research under ignored `build/create-perf/`; conclusions are recorded here.
Decompiled Minecraft names are SRG names; comparisons below concern control flow,
storage layouts, and signatures, rather than textual equality.

## Ranked candidates

Ranking prioritizes useful, contained Solace backports, accounting for compatibility
and evidence. "No numbers" means no benchmark in the inspected commit/associated PR.

| Rank | Upstream commit | Benefit and evidence | Installed 6.0.8 comparison | Mixin size / risk / decision |
|---|---|---|---|---|
| 1 | [5f0910c — funnel extraction / mounted storage](https://github.com/Creators-of-Create/Create/commit/5f0910c6cefa67f53b62a2f0d966bca6162567f8), 2025-12-17, [PR #9706](https://github.com/Creators-of-Create/Create/pull/9706) | Removes duplicate extraction simulation, skips empty slots, avoids unnecessary AABB creation, and changes mounted-slot lookup from O(number of handlers) to O(1). PR screenshots show funnel tick **27.99 ms → 0.12 ms**, ItemHelper extraction **27.8 ms → 0.11 ms**, with two empty full-size vaults. These are results for the entire patch, not this partial port. [Issue #9248](https://github.com/Creators-of-Create/Create/issues/9248) reports 12 TPS with four interfaces and timeouts with twenty; this is a user report, not a controlled before/after measurement. | All four old paths remain: wrapper only constructs `storages`; inherited Forge lookup scans `baseIndex`; funnel creates area before simulation; behaviour simulates then extracts; ItemHelper visits empty slots. Relevant pre-change logic closely matches. | **Implemented mounted lookup only:** one small mixin, one table. Low risk and clear algorithmic gain. The complete four-class patch has wider inventory semantics and addon exposure. |
| 2 | [5c52a4b — Optimizing I](https://github.com/Creators-of-Create/Create/commit/5c52a4b6f9aa6dd495df2b2dcf4af950845a3384), 2025-12-09 | Replaces nested edge-point tick `forEach` lambdas with loops; removes a recipe stream. No numbers. Likely small allocation/dispatch change, no complexity improvement. | `EdgePointStorage.tick` has the same nested lambdas. **SequencedAssemblyRecipe differs:** 1.20.1 lazily scans recipes with `findFirst`; 1.21.1 already builds a result list and uses RecipeHolder/RecipeInput. | Edge-point portion is a small overwrite; recipe part is not a direct port and would change evaluation/short-circuit behavior. Deferred: limited evidence of worthwhile gain. |
| 3 | [5d55d39 — Streaming Trains](https://github.com/Creators-of-Create/Create/commit/5d55d39e798e5a6871de89373fb6bb24a15f9e7d), 2026-01-19 | Builds paired signal-group ID/color lists in one pass, avoiding an intermediate group list and two streams on player login. No numbers; affects login, not steady ticks. | Same three-list/stream pattern exists. Packet APIs differ (Forge `TrainPacket` / AllPackets versus upstream AddTrainPacket / CatnipServices). | Small call-site replacement possible; modest login-only value, deferred. Avoid wholesale playerLogin overwrite. |
| 4 | [ad3ef59 — Optimizing II](https://github.com/Creators-of-Create/Create/commit/ad3ef59ed6fe905a19b27accd3dd7516cee7f83d), 2025-12-09 | Replaces railway manager `forEach` calls with loops; uses `movingTrains.addAll` at load. No numbers, same asymptotic work. HashMap.forEach is already efficient; speedup is not assured on Java 21. | Tick, loading, graph split and login loops match the pre-change pattern. Surrounding networking differs. | Several targeted edits or a larger overwrite. Power Loader hooks tick HEAD/RETURN, which must survive. Deferred for uncertain benefit. |
| 5 | [8f30c2c — Orient data for oriented collision](https://github.com/Creators-of-Create/Create/commit/8f30c2cccce4724ddae1067e4789f56dc3ee5eda), 2025-12-15 | Stores collider centers/extents in dense arrays, avoids AABB materialization/center recomputation and another shape optimize. No numbers. | Installed code has `Optional<List<AABB>>`, repeated AABB center/extent work and `combinedShape.optimize().toAabbs()`. No CollisionList class. It matches the older representation. | Seven upstream files; changes fields/API, carriage overrides and collision implementation. Requires a coordinated adaptation preserving addon APIs; medium/high risk, deferred. |
| 6 | [f2a0e59 — Contraption collision optimization](https://github.com/Creators-of-Create/Create/commit/f2a0e5931ea5b62d7d6d5846752bc95c8adb4bd6), 2026-01-04 | Batches collision checks, densifies viable colliders, reuses a separation manifold, uses scalar accumulators and transposed transforms. Strong potential allocation reduction around large moving structures. No benchmark numbers or associated PR returned by GitHub. | **Not the same input code:** depends on 8f30c2c's absent CollisionList representation. Installed ContinuousOBBCollider still extends OBBCollider and intersects individual AABBs. Matrix3d/OrientedBB APIs differ. | Five files on top of the prerequisite. Hundreds of changed lines, collision math and broad-phase filtering change; large mixin/helper rewrite with movement/step/portal regressions to test. Deferred. |
| 7 | [8155e2f — factory gauge accuracy](https://github.com/Creators-of-Create/Create/commit/8155e2f36351f848eee17d2c5f14d40341fbe7e5), 2025-12-07, PR #9649 | Invalidates summary when links finish loading and calls keepAlive on initialization to avoid unnecessary packages during a stated **20-tick** stale-summary window. Primarily correctness; no throughput benchmark. | Both old paths exist: no immediate invalidation in tickStorageMonitor and no initial keepAlive after linkLoaded. | Two classes, feasible small injections; unrelated to the selected hot inventory lookup and changes logistics state, deferred. |
| 8 | [56e208f — tree fertilizer](https://github.com/Creators-of-Create/Create/commit/56e208f8a84e75fcf7857b7f6c4975e26da35953), 2026-09-16, PR #10742 | Reuses old block state, saving one lookup; mainly fixes drops/replacements and stage handling. No numbers. | 1.20.1 repeats block-state lookup and hardcodes stage 1, but does **not** have upstream's destroyBlock/drop path. | Tiny local optimization possible; no persistent tick benefit, wider correctness patch differs, deferred. |
| 9 | [32e0a2c — clipboard migration](https://github.com/Creators-of-Create/Create/commit/32e0a2c16cb30f2387bb0e50a75797d472ea3643), 2026-05-26, PR #10390 | Replaces registry lookup with instanceof and short-circuits component migration. No numbers. | The targeted Create ItemStackMixin is absent, and MC 1.20.1 does not use the 1.21 data-component constructor. | Not applicable. |
| 10 | [03f8279 — fluid cache validity](https://github.com/Creators-of-Create/Create/commit/03f827991e3802926a652ff269600e499aa269d3), 2026-02-25, PR #10016 | Prevents accessing invalid NeoForge BlockCapabilityCache; crash fix, not a new cache optimization. No numbers. | Installed FlowSource uses Forge LazyOptional and `manageSource(Level)`; the targeted ICapabilityProvider class is absent. | Not applicable; do not transplant NeoForge capability lifecycle code. |

The [upstream license](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md)
licenses code under MIT (assets separately). Port credit is in the mixin and the MIT
notice is packaged at `licenses/create-MIT.txt`.

## Installed mixin checks

Read-only scan of all **483 top-level `.jar` files** in the supplied mods directory,
including recursively embedded jars. Scanned class bytes for Mixin annotations and
target references, then CFR-decompiled matches to distinguish actual annotation
targets from ordinary references. This is a static installed-jar check, not a claim
about which optional mixins the runtime ultimately enables. No changes were made to
mods/config, the main checkout, or other worktrees.

| Target | Actual installed mixins / overlap |
|---|---|
| MountedItemStorageWrapper, Forge CombinedInvWrapper | **None found.** No existing Solace mixin touches either. |
| FunnelBlockEntity | Create Less Ticking 1.0.1: `MixinFunnel` accessors for cooldown; `MixinFunnelImpl` cancels two of three `tick` calls at HEAD and adjusts cooldown. It does not implement any of #9706's four optimizations. |
| GlobalRailwayManager | Create Power Loader 2.0.3: hooks `removeTrain` HEAD and `tick` HEAD/RETURN to manage chunk-loading context. It does not supply upstream loop changes. |
| Contraption | Copycats+ 3.0.10: structure-read/migration RETURN injections; Petrolpark 1.4.27: injection in addBlocksToWorld. Neither supplies collision optimizations. |
| ContraptionCollider, ContinuousOBBCollider, Matrix3d, OrientedBB | No direct target mixins found. Create's own EntityContraptionInteractionMixin targets Entity and references ContraptionCollider; this is a caller, not a direct collision-class mixin. |
| EdgePointStorage, SequencedAssemblyRecipe, ItemHelper, InvManipulationBehaviour, FactoryPanelBehaviour, LogisticallyLinkedBehaviour, FlowSource, TreeFertilizerItem | No direct target mixins found. |

Other initial hits (DD compatibility, Picky Wheels, Sophisticated Core) merely
reference Contraption or CombinedInvWrapper while targeting other classes.
Existing Solace DDCreate6SignatureExtension rewrites incompatible DD mixin signatures,
not these Create methods. Radium, ModernFix, C2ME, and other installed optimization
mods yielded no direct target overlap for the investigated classes. This does not
rule out indirect effects from their Minecraft/Forge patches.

## Implemented scope and configuration

`CreateMountedStorageLookupMixin` adds an override of the inherited protected
`getIndexForSlot` only on Create's MountedItemStorageWrapper. Constructor RETURN
builds an int lookup table from Forge's already captured `baseIndex` / `slotCount`.
Using the existing offsets avoids a second call to addon handlers' getSlots().
Zero-slot handlers and invalid slots preserve Forge routing. The existing O(1)
`getSlotFromIndex` remains authoritative. No extraction, simulation, filtering,
item mutation, networking, tick timing, or other CombinedInvWrapper instances change.

Cost: O(total slots + handlers) construction, approximately **4 bytes per slot** plus
one array header. Lookup becomes O(1), from O(handlers); full scans across S slots
and H handlers no longer pay O(S × H) routing work. Benefit depends on how many
individual mounted handlers the contraption exposes; a single handler has little
to gain. There is no measured in-game gain for this partial backport yet.

The common mixin list covers client, integrated server and dedicated server.
SolaceMixinPlugin requires the target class, **exact Create version 6.0.8**, and
the existing PerformanceConfig master switch plus the new key:

```json
{
  "enabled": true,
  "createMountedStorageLookup": true
}
```

These keys belong in the existing `config/solace-performance.json` alongside its
other settings; preserve the other keys. The new key defaults to true even when
omitted from an older file. Set false and **restart** for an A/B comparison.
`singleplayerOnly` still governs server scheduling experiments; it does not govern
this common, world-independent startup patch. A config reload cannot remove a
mixin already applied. Unknown Create versions retain their original behavior.

No Create compileOnly dependency is needed: this mixin uses a string target,
constructor callback without Create-typed parameters, and Forge's public superclass.

## Validation performed

- `JAVA_HOME=C:/Program Files/Java/jdk-17 ./gradlew build --console=plain -q` passed
  online (existing deprecation / annotation-processor warnings).
- `py tools/create-perf/verify.py` passed: actual Mixin 0.8.5 transforms the installed
  Create class using the mixin **from the built jar**, then instantiates it with test
  handlers. Compares against actual Forge 47.4.15 CombinedInvWrapper across **250
  layouts / 122,890 slot checks**, including zero-slot handlers, empty maps, invalid
  indices, and inherited public-method dispatch/local-slot translation. The test
  substitutes only an inert ItemStack type token; it never bootstraps game registries.
- `git diff --check` passed. Inspected built jar for mixin registration and MIT notice.
- Minecraft was not launched. Full pack runtime compatibility and item conservation
  remain part of the following manual test plan.

## In-game A/B test plan (not run)

1. Use a test world with the same pack and Java 21. Restart between config false and
   true; keep view distance, contraptions and surrounding activity identical. Warm up
   the world before collecting equal-duration Spark server profiles (e.g. 60 seconds).
2. Assemble a stationary train or bearing contraption with **many separate chests /
   barrels**, e.g. 64–256, glued onto it. Add mounted portable storage interfaces and
   matching fixed interfaces feeding brass funnels into belts and receiving storage.
   Ensure the interfaces stay connected. Separate inventories stress handler lookup
   more than one large handler. Keep a single-chest version as a control.
3. Test empty inventory, sparse inventory with items in a late handler, and sustained
   extraction/insertion across many handlers. Repeat with the upstream-style case of
   two full-size vaults and several interfaces/funnels. Also test while disconnected
   so the scan workload and any Create Less Ticking effects are clear.
4. Compare server MSPT and samples beneath ItemHelper, portable storage interface
   handlers, MountedItemStorageWrapper, and CombinedInvWrapper.getIndexForSlot.
   Expect reduced routing work, not the entire upstream PR's 27.99→0.12 ms result;
   empty-slot max-stack-size work remains in this partial port.
5. Check filters, exact-count and up-to extraction, partially full/full output,
   simulation without removal, and no duplicate/lost stacks. Count items before and
   after. Include mixed addon inventories, assemble/disassemble, reconnect interfaces,
   unload/reload chunks, save/reopen, and client reconnect to dedicated server.
6. Confirm no mixin-application errors; set the key false and restart to verify the
   baseline. Railway signaling/collision behavior should remain the existing pack's
   behavior, since those candidates were deferred.
