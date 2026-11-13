# Structure gates

A **structure gate** keeps a structure out of world generation until something opens it. While the
gate is open the structure generates through the normal structure system (its own structure set,
spacing, biomes, Structurify settings) in chunks generated from then on. Nothing is ever placed at a
chosen position. Code: `src/main/java/com/solace/world/gates/`.

## Config — `config/solace-structure-gates.json`

Which structures are gated. No structure id is in Java; edit the file and run `/solace gates reload`
(or `/reload`). Written empty if missing.

```json
{
  "version": 1,
  "gates": [
    { "structure": "irons_spellbooks:evoker_fort", "mode": "once" },
    { "structure": "#astralis:host_outposts", "mode": "infinite" },
    { "structure": "mischief_illagers:imprecator_hut", "mode": "infinite", "initially_open": false }
  ]
}
```

| Field | Meaning |
|---|---|
| `structure` | A structure id, or a structure tag `#namespace:path` (expanded when the registry/tags load, again on every tag reload). |
| `mode` | Default mode when the gate is opened without one: `once` or `infinite` (default `infinite`). |
| `initially_open` | Default `false`: closed from world creation. |

- Invalid ids/modes are logged and the entry skipped (bad mode → `infinite`).
- An id or tag that does not exist (yet) is logged once and **kept**; it applies as soon as it exists
  (e.g. the planned `astralis:*` structures).
- A structure in two entries uses the first one.
- Removing a structure from the file un-gates it (Structurify alone decides again). A new entry starts
  closed unless `initially_open`. World state for removed entries is kept, not deleted.
- State is **per structure**, also for tag entries: opening `#astralis:host_outposts` in `once` mode
  would allow one of *each* member, not one in total.

## World state

`data/solace_structure_gates.dat` in the overworld. Per structure that something happened to: open or
closed, mode, and for `once` gates the position(s) of the copy that generated (dimension + the start's
bounding-box centre). Structures with no record follow the config default. One state for the whole
world: a gate opened by one player is open for everyone, in every dimension.

## Modes

- **closed** — the structure never generates. Chunks generated while closed never get one later.
- **`infinite`** — generates normally, as many as worldgen makes.
- **`once`** — the first valid start that is about to be placed claims the gate: its position is
  saved and the gate closes in the same synchronized step, so two worldgen threads cannot both place
  one. Every later attempt is refused (the structure set then tries its other structures, like
  vanilla does for an invalid start).

## The switch

All of these call the same code (`StructureGates.open/close/reset`).

```
/solace gates list                                   every gated structure: state, mode, saved position
/solace gates status <structure|#tag>
/solace gates open <structure|#tag> [once|infinite]  mode defaults to the config's
/solace gates close <structure|#tag>
/solace gates reset <structure|#tag>                 back to the config default, forget saved positions
/solace gates reload                                 re-read the config
```

Permission level 2; suggestions come from the config. `open` is idempotent: an open gate stays as is,
and a `once` gate whose copy already generated is **not** reopened (use `reset` first to get another
copy, or open it as `infinite`).

Quest effects (see `quests/AUTHORING.md` → *Effects*):

```json
{ "type": "solace:open_structure_gate", "structure": "irons_spellbooks:evoker_fort", "mode": "once" }
{ "type": "solace:close_structure_gate", "structure": "#astralis:host_outposts" }
```

KubeJS (`SolaceQuests` binding, see `quests/KUBEJS.md`):
`openStructureGate(server, structure, mode)`, `closeStructureGate(server, structure)`,
`structureGateStatus(server, structure)`. Datapack functions can run the command.

## Locators

`solace:give_structure_map` and `solace:give_structure_compass` (fields in `quests/AUTHORING.md`).
Both search off the server thread on a dedicated thread, through the gate-aware search below.

- **Map**: built like the vanilla `exploration_map` loot function (zoom 2, biome preview, target
  decoration, skips already-explored structures). Nothing found → the player gets a blank
  `minecraft:map` carrying the search ("The ink won't settle…"); using it searches again and turns it
  into the filled map.
- **Compass**: any item (default `minecraft:compass`) with a lodestone-style target in NBT
  (`LodestonePos`, `LodestoneDimension`, `LodestoneTracked: false`); nothing found → dimension only,
  so a vanilla compass needle spins. Using it searches again. With `sound`, it hums every 2 s while
  held, louder and higher the closer the target (full volume at the target, quiet beyond ~1000
  blocks). A custom item only shows a needle if its model reads the vanilla compass angle.
- Locator items carry a `SolaceStructureLocator` NBT compound; right-click is taken over (vanilla
  use is cancelled) and has a 2 s cooldown.

## Structurify

Structurify 2.0.43 (decompiled) applies a per-structure disable only by reading
`StructureData#isDisabled()`, from:

- its `@WrapMethod` on `ChunkGenerator#tryGenerateStructure` (returns false before generating),
- `StructurePlacementResolver#generateStructure` (its multi-attempt placement),
- its `@WrapMethod` on `ChunkGenerator#findNearestMapStructure` (null if every requested structure is disabled),
- its `LocateCommand` wrapper (throws "structure is disabled").

It does **not** remove disabled structures from the structure set (only weight 0 and whole-set
disables do that, and those are set settings we keep).

What Solace does:

1. `StructurifyStructureDataMixin` (`@Pseudo`, `require = 0`): for a gated structure
   `isDisabled()` returns `false`, so Structurify never refuses it and **all its other checks still
   run** (biomes, flatness, distance from centre, overlap, spacing/separation, pools, weights).
   Ungated structures are untouched.
2. Solace's own hooks then refuse closed gates: `StructureGateChunkGeneratorMixin` injects at the
   head of `tryGenerateStructure` (inside Structurify's wrapper, i.e. after its checks) and claims
   `once` gates at `StructureStart#isValid()`; `StructurifyPlacementResolverMixin` refuses closed
   gates in Structurify's placement resolver so they cannot win a resolved chunk.
3. `StructurifyStructureDataSerializerMixin` makes Structurify save its own stored value, so
   `config/structurify.json` is never rewritten with the gate's answer.

So `illagerstructures:illager_floating_outpost`, disabled in Structurify, generates when its gate is
open. Structurify's in-game config screen shows gated structures as enabled (that toggle has no effect
on them while they are gated). Without Structurify the Structurify mixins are skipped (mixin plugin
checks for its class) and the vanilla hooks work alone.

## Locate behaviour

`StructureGateChunkGeneratorMixin` hooks `ChunkGenerator#findNearestMapStructure`, which every search
goes through: `/locate structure`, `ServerLevel#findNearestMapStructure` (explorer maps, cartographer
trades, dolphins, eyes of ender), **async-locator** (its mixins redirect those calls onto its own
thread but still call the same two methods), Solace quest markers and the locator effects.

| Gate | Result |
|---|---|
| not gated | normal |
| closed, nothing generated | skipped (no result; `/locate` says it found none) |
| open `infinite` | normal search |
| `once`, generated | the saved position (any distance, ignores "skip known") |
| `once` open, not generated yet | normal search; that copy claims the gate when its chunk generates |

When a request mixes gated and ungated structures (a tag), the nearest of all allowed answers wins.

- **Traveler's Compass** (3.1.03) searches items, blocks and entities in loaded chunks only; it has
  no structure search, so there is nothing to gate.
- **Solace markers** (`quests/state/Markers.java`): a "nothing found" result is cached with the gate
  version and searched again after any gate change (open/close/reset/reload/once claim). Found
  positions stay cached per player as before.

## Limitations

- Closed means never in chunks generated while closed: no retrofitting, by design.
- The saved `once` position is the start's bounding-box centre; `/locate` and maps point there.
- A `once` gate's "open, not generated" search predicts where vanilla placement would put it; if that
  start then fails (terrain, jigsaw), a different chunk claims the gate and later searches return
  that one.
- `locate` re-entry is guarded per thread; a mod calling `findNearestMapStructure` for a gated
  structure from another code path still goes through the hook.
- Explorer maps, trades and dolphins for closed structures simply find nothing (vanilla behaviour
  for a missing structure: no trade offer, empty map).

## Superseded prototype

`kubejs/server_scripts/commands.js` (`/set_structure_unlocked`) and `kubejs/startup_scripts/entity.js`
(`structure_test` entity running `/place structure`) were an earlier take on this. They are left in
place for now; delete them once the gates are confirmed in play (ask first).
