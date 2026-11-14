# Brief: Solace structure gates

You are building a feature in **Solace** (Forge 1.20.1, Forge 47.4.x, Java mod at `Solace/` inside the Astralis modpack instance). Read `AGENTS.md` at the instance root first and follow it (Mnemosyne recall/handoff, preserve user changes, keep changes scoped). Do not commit unless asked. Do not launch the CurseForge game instance without asking; Gradle builds and Solace's own dev runs (`Solace/run`) are fine.

## Goal

Quest structures in Astralis must not exist in the world until the story reaches them. A **structure gate** keeps a structure out of world generation until something opens it. Once open, the structure generates through the **normal random structure system** (its own structure set, spacing, biomes) in chunks generated from then on. Nothing is ever placed at a chosen position or distance.

Design reference (read it): the design note "Quest Structure Placement" (Astralis Chapter 1, Systems). It holds the gate list and how quests use gates.

## Requirements

### 1. Config, not code

- One config file, `config/solace-structure-gates.json`, lists every gated structure. **No structure id is hard-coded in Java.** Adding a structure to the system = editing this file, no rebuild.
- Follow the existing config style in `src/main/java/com/solace/config/` (Gson, pretty-printed, written with defaults if missing, logged and ignored entries on bad ids — see `VillageBuildingSpawnConfig`).
- Entries accept a structure id **or a structure tag** (`#namespace:path`), expanded when the registry is available.
- Proposed shape (adjust if you find a better one, and document it):

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

  - `mode`: the default mode when the gate is opened without one (`once` or `infinite`).
  - `initially_open`: default `false` (closed from world creation).
- Reloadable at runtime: on `/reload` and on `/solace gates reload`. A structure removed from the file stops being gated; a new one starts closed (unless `initially_open`).

### 2. Per-world state

- Gate state lives in the world (overworld `SavedData`), not in the config: for each gated structure — `closed` / `open` + mode, and for `once` gates the saved position(s) of the copy that generated (structure id → dimension + BlockPos of the start).
- State survives restarts. The config defines *which* structures are gated; the world remembers *what happened*.

### 3. Modes

- **`infinite`**: while open, the structure generates normally, as many as world generation makes.
- **`once`**: while open, the **first** start of that structure that generates is allowed; at that moment its position is saved and the gate closes again. Every later attempt is refused. Chunk generation is multi-threaded: the claim must be atomic so two worker threads cannot both place one.
- **Closed**: the structure never generates. Chunks generated while closed never get one later (no retrofitting) — that is intended.

### 4. The switch: a command, plus a quest effect that calls it

Use a **server command as the single switch**, so quests, KubeJS, datapack functions and operators all use the same path:

```
/solace gates list                                   – every gated structure, state, mode, saved position
/solace gates status <structure>
/solace gates open <structure|#tag> [once|infinite]  – mode defaults to the config's
/solace gates close <structure|#tag>
/solace gates reset <structure|#tag>                 – back to closed and forget saved positions (testing)
/solace gates reload
```

Permission level 2. Suggestions for ids come from the config's entries.

Also add a native quest effect so quest JSON stays readable (Solace's effects are listed in `docs/quests/AUTHORING.md` → *Effects*):

```json
{ "type": "solace:open_structure_gate", "structure": "irons_spellbooks:evoker_fort", "mode": "once" }
```

It calls the same code as the command, is world-wide (the first player to fire it opens it for everyone) and idempotent. Add `solace:close_structure_gate` too. Expose the same calls to KubeJS (see `docs/quests/KUBEJS.md` for how Solace exposes things).

### 5. Structurify: override enable/disable only

Astralis uses **Structurify** (`mods/structurify-forge-2.0.43+mc1.20.1.jar`, config `config/structurify.json`). For any structure listed in the gate config:

- The **gate decides whether it may generate**, overriding Structurify's `is_disabled` both ways: a gated structure that Structurify disables can still generate when its gate is open (example: `illagerstructures:illager_floating_outpost` is disabled in Structurify today and will be gated instead).
- **Every other Structurify setting still applies**: biome whitelist/blacklist, spacing, separation, flatness checks, structure-set and template-pool settings, weights.
- Structures *not* in the gate config are untouched; Structurify keeps full control.

Investigate how Structurify applies `is_disabled` before choosing the hook (decompile the jar). It may remove the structure from the set's placement, cancel at structure-start generation, or both. Pick the hook that lets the gate take precedence without re-implementing Structurify's other checks — likely a mixin into Structurify's own disable check, or a vanilla hook that runs where Structurify's does, with clear priority. Use `@Pseudo` / `require = 0` patterns so Solace still loads without Structurify. Write down what you found in the doc.

### 6. Locating must respect gates

Anything that searches for structures must agree with the gates, or players get maps to things that cannot exist:

- `/locate structure`, `ServerLevel#findNearestMapStructure` and `ChunkGenerator#findNearestMapStructure` (used by explorer maps, villager cartographer trades, dolphins, eyes of ender style searches).
- `async-locator` (installed) wraps those calls — check it still goes through your hook.
- Traveler's Compass (installed) — check how it searches structures and that it respects gates.
- Behaviour: **closed** → skipped (no result). **open infinite** → normal. **`once` already generated** → returns the saved position. **`once` open, not yet generated** → normal search (it will generate at the found spot when that chunk is created; when that happens the gate closes and later searches return the saved position).
- Solace's own structure markers (`src/main/java/com/solace/quests/state/Markers.java`, `locate(...)`) cache positions per player; make sure they don't cache a "none" result forever while a gate is closed and then opened.

### 7. Locator effects (second part — do after 1–6 work)

The quests hand the player an item that finds the structure:

- `solace:give_structure_map` — `structure` (id or tag), `name`, `lore`, `decoration` (map decoration type), `search_radius` (chunks, default 100). An explorer map built like the vanilla `exploration_map` loot function, async if possible. If nothing is found, give a blank map with a "the ink won't settle" style message and retry when used, instead of failing silently.
- `solace:give_structure_compass` — `structure`, `item` (default `minecraft:compass`, may be a custom item like `kubejs:tuning_fork`), `name`, `lore`. A lodestone-style compass target stored in NBT; using the item re-runs the search and refreshes the target. Optional `sound` that plays louder as the player gets closer (the Tuning Fork hums).

Both use the gate-aware search from part 6.

### 8. Bosses (optional, only if small)

`solace:find_structure` already exists. If it is cheap, add a way for a phase to spawn a named boss on the player's first entry into a structure (the vault calls it `spawn_boss`). If not cheap, skip it and say so.

## Initial config to ship

Seed `config/solace-structure-gates.json` (the instance copy, not just the in-jar default) with the gate list from the vault note. Structure ids:

| Structure | Mode |
|---|---|
| `astralis:convoy_cart` (custom, does not exist yet — log and skip missing ids) | once |
| `#astralis:host_outposts` (towns_and_towers pillager outposts — the tag may not exist yet: create it in the astralis paxi datapack under `tags/worldgen/structure/` listing the outpost ids) | infinite |
| `irons_spellbooks:evoker_fort` | once |
| `illagerstructures:smeltery` | infinite |
| `conjurer_illager:theatre` | infinite |
| `mischief_illagers:imprecator_hut` | infinite |
| `companions:companions_factory` | infinite |
| `astralis:grey_hall` (custom, not yet) | once |
| `minecraft:mansion` | stays closed (gated, never opened in Chapter 1) |
| `goety:black_assembly` | infinite |
| `illagerstructures:lava_extractor` | infinite |
| `irons_spellbooks:pyromancer_tower` | once |
| `astralis:okkum_still` (custom, not yet) | once |
| `astralis:trial_chamber/hollow` (custom, not yet) | once |
| `dungeonnowloading:labyrinth` | once |
| `astralis:crossing_place` (custom, not yet) | once |
| `idas:enchantingtower` | infinite |
| `mvs:cartographer_tower` | once |
| `irons_spellbooks:ice_spider_den` | infinite |
| `illagerstructures:illager_floating_outpost` | infinite |

Missing ids must not crash: log once, keep the entry, apply it when the id exists.

## Existing pieces to replace or keep

- `kubejs/server_scripts/commands.js` (`/set_structure_unlocked`) and `kubejs/startup_scripts/entity.js` (`structure_test` entity running `/place structure`) are an old prototype of this idea. Leave them in place; note in the doc that they are superseded, and ask the user before deleting.
- Don't touch `config/structurify.json`.

## Done means

1. `./gradlew build` passes in `Solace/`.
2. A doc `Solace/docs/structure-gates.md`: config format, commands, effects, modes, the Structurify interaction you implemented, locate behaviour, limitations.
3. `docs/quests/AUTHORING.md` lists the new effects.
4. Tested in a dev world (ask before launching anything): a gated structure never appears and `/locate` finds nothing while closed; after `/solace gates open … infinite` it appears in new chunks and `/locate` finds it; `once` produces exactly one and then `/locate` returns that one; a Structurify-disabled structure that is gated generates when open; a non-gated structure's Structurify settings are unchanged; restart keeps state; editing the config and `/solace gates reload` picks up a new structure without a rebuild.
5. Report what you could not test, and store the handoff in Mnemosyne as `AGENTS.md` asks.
