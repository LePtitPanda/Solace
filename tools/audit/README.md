# Private temporary performance audit

This is an internal attribution tool for Astralis, compiled only through `audit-jar.gradle`. A normal clean Solace build excludes `src/audit` completely. Captures are explicitly requested, last 5–300 seconds, and write only under the instance's `local/solace-audit/`. There is no upload, HUD, FPS counter, dashboard, or gameplay preparation.

Build from the instance root with `./Solace/tools/audit/build.ps1`. It extracts the already installed MixinExtras compile dependency, builds/reobfuscates using Java 17, and saves `local/solace-audit-2026-10-13/solace-audit.jar`. Back up the installed Solace JAR before installing this artifact; never replace a different concurrent build without checking its hash. Restart Minecraft to load the hooks. The game's normal launcher works; Codex Control is optional.

Use the bundled Python runtime (or any Python 3.11+) to run:

```text
python Solace/tools/audit/audit.py status
python Solace/tools/audit/audit.py start natural-play --seconds 60
python Solace/tools/audit/audit.py start profiler-control --seconds 60 --no-probes --no-gpu
python Solace/tools/audit/audit.py start detailed-scene --seconds 15 --sample-one-in 1
python Solace/tools/audit/audit.py stop
python Solace/tools/audit/audit.py analyze local/solace-audit/<capture-directory>
```

The file controller is initialized on the first client tick or server command registration. It ignores old control requests on restart, polls once per second, acknowledges request IDs in `status.json`, and automatically ends a capture. Send requests after it initializes. Internal server commands `solaceaudit start <label> <seconds>` and `solaceaudit stop` are also available with operator permission. No configuration or JVM flag is needed beyond loading the temporary JAR.

Outputs:

- `metadata.json`: Java, heap limit, loaded mods, requested instrumentation, capture coverage.
- `content.json`: sampled inclusive wall time, thread CPU, allocated bytes, counts, slowest observed location, thread/dimension, scene settings, asynchronous GPU render durations. Entity/block-entity ticks, scheduled/random block/fluid ticks, entity/block-entity renderer CPU, particle tick/render CPU, placed features, structure generation and placement are covered through shared vanilla dispatch paths. Custom systems bypassing these paths require JFR or follow-up hooks. The optional per-particle render injection may be bypassed by renderer replacements; absence is inconclusive.
- `profile.jfr`: Java's profile recording, bounded to 256 MiB and 330 seconds. CPU/native samples are requested every 20 ms; allocation sampling is weighted, with a 150/s throttle. GC, waits, IO, and sampled slow content events remain available. Size bounds can discard early events. Whole-game native/GPU/driver internals are outside JFR's coverage.
- `class-owners.tsv`, `class-index.json`: offline exact class-to-JAR index, including nested libraries and ambiguous ownership. Duplicate root JARs are excluded when the runtime loaded filename identifies another copy. Update-date static inventory supplies IDs when runtime filenames cannot identify a JAR; inspect unknown/ambiguous entries rather than trusting guessed ownership.
- `jfr-summary.json`, `attribution.json`: offline ranked reductions by content, thread, owner, allocations, waits/IO, GPU durations, and GC pauses. Full raw data is retained. Java reduction streams events and keeps the top 4000 rows per table; truncation is reported.
- Optional `audit.py monitor-gpu` records whole-adapter utilization/VRAM/clock/power through a local `nvidia-smi` executable, if available. Other applications can contribute. It cannot assign VRAM or GPU time to mods.

Default probes randomly sample one call in 32. Structure generation/placement always inspect every call because rare giant structures should not be missed. Use each row's `sampleOneIn` for estimates. Caps: 4096 keys per thread, 128 contributing threads, 65536 total keys, 120 scene snapshots, 20000 GPU values, 8 pending GPU query pairs. Drops and incomplete drain are reported. No entity/world objects are retained in aggregation rows. Export waits up to five seconds off the game threads for outstanding sampled calls, then safely snapshots completed samples under per-buffer locks. If the drain is incomplete, outstanding spans are absent and `complete=false`; completed samples remain available.

GPU timestamps bracket `GameRenderer.render` every 16 calls and are read only after availability, using separate timestamp query objects to coexist with other timers. They include GUI rendering and exclude later presentation work. They cannot assign batched GPU draws to individual mods. Renderer probe rows measure CPU submission, never GPU time. No blocking GPU read or `glFinish` is used.

Interpretation discipline: costs are inclusive, categories can overlap, and summing them produces double counting. Per-probe CPU/allocation counters have overhead. JFR sees the profiler's own allocations too. Ownership of an entity/feature or the nearest mod stack frame is evidence, not a causal removal-gain measurement. Low sample counts and absent rows are inconclusive. Java allocations do not measure retained heap or VRAM. Compare repeated, warmed, identical scenes with probes disabled/enabled to estimate perturbation; use Spark's existing measurements for frame/tick performance. Use controlled feature toggles to establish actual gains. Rankings from this laptop cannot predict exact savings on another PC.

Validation sources: `AuditSelfTest.java` exercises concurrent accounting, exception cleanup, disabled mode, key caps, custom JFR events, and a core-only overhead microbenchmark. It is not compiled into the game. `AnalyzeRecording.java` runs offline on Java 17 with no third-party dependencies. Runtime hook compatibility, GPU query behavior, and inactive/active overhead require actual-pack verification in addition to compilation.

Removal: replace the temporary JAR with a clean normal Solace build containing current compatibility fixes. Keep the audit sources/tools for reuse or delete only `src/audit` and `tools/audit` after saving desired captures. Do not restore an older pre-update JAR that still targets incompatible Fragmentum APIs.
