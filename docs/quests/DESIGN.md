# Solace Quests — Design

Status: **approved 2026-11-12** with the §1 recommendations. Phase 2 (core) is implemented; see §16.
Where the code deliberately differs from this plan it is marked **As built**.
Branch: `feature/solace-quests` in the `Solace` submodule.

Solace Quests is a feature module inside the Solace mod (Forge 47.4.15, MC 1.20.1, official
mappings, mod id `solace`). It replaces FTB Quests, Blabber and ArcQuest in the Astralis pack with
one system: questlines, branching dialogue, factions/reputation, map markers and a web editor.

Provenance: written from the project spec. ArcQuest and FTB Quests are feature inspiration only;
no code, assets, file formats or field names are taken from either. All JSON below is original.

---

## 1. Decisions needed before phase 2

These are the only points where the spec's "ask before" rule applies or where I made a call you
may want to overrule. Each has a recommendation; everything else in this doc follows from them.

| # | Question | Recommendation |
|---|----------|----------------|
| 1 | **Unit tests need a test framework.** `build.gradle` has no JUnit; `src/test` only holds the benchmark mixins. | Add `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'` (test-only, never shipped). Alternative with zero new dependencies: plain `main()` checks under `tools/quests/` wired into `check`, like `verifyNoiseHashMemo`. |
| 2 | **Editor toolchain.** ES modules do not load from `file://`, so "works offline as a static page" needs either a bundler or classic scripts. | TypeScript + Vite + `vite-plugin-singlefile` (dev-only npm packages), output one `index.html` that is both the offline page and the file the mod serves. Alternative: hand-written classic `<script>` files, no build, no types. |
| 3 | **Editor HTTP server.** | JDK built-in `com.sun.net.httpserver` — no library. If the launcher's bundled Java runtime turns out to lack the `jdk.httpserver` module (checked at the start of phase 8), fall back to a ~150-line `ServerSocket` handler. No Netty/Jetty dependency either way. |
| 4 | **Villager faction scope.** Spec allows one "villagers" faction or per-village. | One faction, standing = average vanilla gossip reputation of villagers within 48 blocks of the player (what Thief itself shows), last value cached while away. Per-village is a v2 item: it needs a village identity that vanilla does not have. |
| 5 | **Spoilers.** Should the client receive definitions of hidden quests? | No. Hidden quests and locked chapters are sent only once visible to that player. Costs a little sync logic, keeps the book un-datamineable through the packet stream. |
| 6 | **KubeJS / Thief / Xaero / JEI / EMI** jars added as `compileOnly files('../mods/…')`. | Yes, same as every other Solace compat. Not bundled, not required at runtime. |

---

## 2. Scope

**v1**: chapters, quests, phases, objectives, rewards, conditions, flags/variables, dialogue with
NPC bindings, quest book, journal, HUD tracker, toasts, factions with Thief/vanilla-gossip bridge,
markers with Xaero bridge and built-in compass, admin commands, hot reload with per-file
validation, KubeJS events and custom types, localhost web editor, Blabber migration script.

**v1.x**: JEI/EMI "used in / rewarded by quest" lookups, light editor theme, in-world marker beam
polish, dialogue "open shop" action.

**v2**: shared party progress (see §7.3), per-village factions, dedicated-server editor workflow
beyond the opt-in config flag, quest book search.

Out of scope: any change to existing Solace features or configs.

---

## 3. Module layout

Server/common code under `com.solace.quests`, client under `com.solace.client.quests`.

| Package | Responsibility |
|---------|----------------|
| `quests.content` | Immutable definitions (`Chapter`, `Quest`, `Phase`, `Dialogue`, `NpcBinding`, `Faction`, `MarkerDef`), `QuestContent` (one loaded snapshot), `QuestContentLoader` (reload listener), `JsonReader` (path-tracking parser), `ValidationReport`. |
| `quests.condition` | `Condition` interface, built-in types, `ConditionTypes` registry. |
| `quests.objective` | `Objective` definitions, `ObjectiveTypes` registry, `ObjectiveIndex` (type + target → objectives), `ObjectiveEvents` (Forge listeners). |
| `quests.reward` | `Reward` types and registry, claim logic. |
| `quests.action` | Shared action list used by dialogue nodes/choices, phase enter/exit, transitions. |
| `quests.state` | `QuestSavedData`, `ProgressOwner`, `PlayerQuestData`, `QuestEngine` (the only class that mutates state). |
| `quests.dialogue` | `DialogueSession`, `DialogueManager`, NPC binding matcher, NPC freeze/look-at. |
| `quests.faction` | Reputation store, rank lookup, relation propagation, gossip bridge. |
| `quests.marker` | Server-side marker resolution and refresh timer. |
| `quests.network` | `QuestNetwork` (SimpleChannel `solace:quests`), packets. |
| `quests.command` | `/solacequests …`. |
| `quests.editor` | Localhost HTTP server, registry export, file writer. |
| `quests.compat` | `ThiefCompat`, `KubeJsCompat` (+ plugin), guarded by `ModList.get().isLoaded`. |
| `client.quests` | Client content/state mirror, `QuestBookScreen`, `JournalScreen`, `DialogueScreen`, `QuestHud`, toasts, keybinds, `XaeroMarkerCompat`, compass fallback. |

**As built (phase 2):** fewer packages than planned — `quests` (`SolaceQuests` wiring and event
handlers, `QuestCommands`), `quests.content` (definitions, `In` parser, loader), `quests.condition`,
`quests.state` (`PlayerQuestData`, `QuestSavedData`, pure `QuestLogic`, server-side `QuestEngine`
with reward granting). The rest of the table appears as its phase lands.

Wiring in `Solace.java`: one line, `SolaceQuests.init()`, next to
`LootEditorNetwork.register()`. No mixins are planned for phases 2–5; if NPC freezing needs one in
phase 3 it will be called out then.

UI: every screen implements `SolaceThemedScreen` and draws with the
`WorldCreationThemeRenderer` constants (`BACKGROUND 0xFF07070F`, `PANEL 0xFF0C0C1C`,
`GOLD 0xFFD4A843`, `GOLD_DIM 0xFF6A4E18`, `TEXT 0xFFDDDDEE`, `MUTED 0xFF7777AA`); dialogue choices
and chapter entries reuse `SolaceChoiceCard`; destructive confirms (abandon quest) reuse
`SolaceHoldButton`.

---

## 4. Content loading

### 4.1 Sources and precedence

1. **Datapack resources** — `data/<namespace>/solace_quests/<kind>/**/*.json`, read through
   `ResourceManager.listResources` + `Resource.open()` streams. Normal datapack stacking applies
   (the highest pack wins). Astralis ships these through Paxi at
   `config/paxi/datapacks/astralis/`.
2. **Loose files** — `config/solace/quests/<namespace>/<kind>/**/*.json`, written by the editor.

**Loose files win** over datapack entries with the same id. A loose file containing exactly
`{"removed": true}` hides the datapack entry (so the editor can delete shipped content).

`<kind>` is one of `chapters`, `quests`, `dialogues`, `npcs`, `factions`, `markers`.

### 4.2 Ids

The id is the file location: `data/astralis/solace_quests/quests/village/first_steps.json` →
`astralis:village/first_steps`. There is no `id` field; if a file contains one it is a validation
warning and is ignored.

### 4.3 Limits

Enforced while reading the stream (a counting `InputStream`), never through `Files.size` or any
path API — datapack resources have no real path, which is what broke ArcQuest 1.0.9 here.

- 512 KiB per file, 32 MiB per reload, 20 000 files per reload, JSON nesting depth 64.
- A file over the limit is rejected like any other invalid file (§4.4); the reload continues.

### 4.4 Validation and hot reload

Reload runs as a `SimplePreparableReloadListener` on `AddReloadListenerEvent` (so `/reload` and
`/solacequests reload` both work) in three steps:

1. **Parse** each file on its own. `JsonReader` records the JSON path of every problem, e.g.
   `astralis:village/first_steps (quests) $.phases[1].objectives[0].count: expected integer >= 1, got "three"`.
2. **Link** the candidate set: every reference (quest → chapter, dependency → quest, action →
   dialogue/quest/phase/faction, binding → dialogue, marker id) must resolve. Registry ids
   (items, entities, structures…) are checked against the live registries; unknown ones are
   **warnings**, because the mod that owns them may simply be absent.
3. **Apply**. A file with any error is not applied: the previous valid version of *that file* is
   kept (or it is absent if there never was one). Link is then re-run once against the final set
   so a fallback version cannot leave dangling references; anything still dangling is disabled
   and reported.

The whole reload is never rejected. The report goes to the log, to the command sender, to
`/solacequests validate`, and to the editor's validation panel as
`{file, kind, id, path, severity, message}` records.

After apply: rebuild `ObjectiveIndex`, re-evaluate availability for online players, send a fresh
content + state snapshot. Player state referring to ids that no longer exist is kept and flagged
`orphaned` (§7.4).

### 4.5 Text

Every player-facing string is a vanilla text component in JSON: a plain string is literal,
`{"translate": "astralis.quest.first_steps.title"}` is a key, and colours/formatting work as in
`/tellraw`. Parsed with `Component.Serializer`; no custom format.

---

## 5. Data formats

Optional fields are omitted from the examples when left at their default. Full field tables go in
the authoring guide (`docs/quests/AUTHORING.md`, written alongside phase 2).

### 5.1 Chapter — `chapters/village.json`

```json
{
  "title": "The Village",
  "description": "Find your feet among the people of Brindle Hollow.",
  "icon": { "item": "minecraft:bell" },
  "order": 10,
  "unlock": { "type": "solace:quest_state", "quest": "astralis:intro/arrival", "state": "completed" },
  "hidden_until_unlocked": false
}
```

### 5.2 Quest — `quests/village/first_steps.json`

```json
{
  "chapter": "astralis:village",
  "title": "First Steps",
  "description": "The cleric wants proof you can handle yourself.",
  "icon": { "item": "minecraft:iron_sword" },
  "position": { "x": 2, "y": 0 },
  "requires": { "mode": "all", "quests": ["astralis:intro/arrival"] },
  "optional": false,
  "hidden_until_unlocked": false,
  "auto_start": false,
  "repeatable": { "cooldown_seconds": 0, "enabled": false },
  "time_limit_seconds": 0,
  "auto_claim": false,

  "phases": [
    {
      "id": "hunt",
      "story": "Marrow asked me to thin out the dead near the old mill.",
      "objectives": [
        { "id": "zombies", "type": "solace:kill", "entity": "minecraft:zombie", "count": 3,
          "text": "Slay zombies" },
        { "id": "mill", "type": "solace:reach_location", "optional": true,
          "dimension": "minecraft:overworld", "pos": [212, 71, -340], "radius": 12,
          "text": "Look around the old mill" }
      ],
      "markers": [ { "target": "objective", "objective": "mill", "label": "Old mill" } ],
      "transitions": [
        { "to": "report", "when": { "type": "solace:objectives_complete" } }
      ]
    },
    {
      "id": "report",
      "story": "The mill is quiet again. Marrow will want to hear it.",
      "objectives": [
        { "id": "talk", "type": "solace:dialogue_node",
          "dialogue": "astralis:cleric/first_steps", "node": "report_done",
          "text": "Return to Marrow" }
      ],
      "markers": [ { "target": "npc", "npc": "astralis:cleric", "label": "Marrow" } ],
      "transitions": [
        { "to": "@complete", "when": { "type": "solace:objectives_complete" } }
      ]
    }
  ],

  "rewards": [
    { "id": "xp", "type": "solace:xp", "levels": 3 },
    { "id": "bread", "type": "solace:item", "item": "minecraft:bread", "count": 6 },
    { "id": "rep", "type": "solace:reputation", "faction": "astralis:villagers", "amount": 15 }
  ]
}
```

Rules:

- `requires.mode`: `all`, `any`, or `{"at_least": N}`.
- A quest with no `phases` may put `objectives` at the top level; it is treated as one phase
  named `main` with an implicit transition to `@complete`.
- `transitions[].to` is a phase id, `@complete` or `@fail`. Transitions are evaluated in order,
  first match wins, whenever something the conditions depend on changes (objective progress,
  flag, variable, reputation, dialogue action). A transition may carry `"actions": […]` and a
  `"label"` recorded in the quest history (§7.2).
- Branching by choice is a dialogue action (`solace:set_phase`) or a flag checked by a
  transition; there is no separate branch construct.
- `time_limit_seconds > 0` fails the quest when it runs out (checked on a 1 s timer, only for
  quests that have a limit). `on_fail: "reset" | "keep"` decides if it can be retried.
- Objective/reward/phase `id`s are required and unique within their quest: saved progress is
  keyed by them, so reordering a list never corrupts progress.

### 5.3 Objective types

`type`, then type-specific fields. All share `id`, `text`, `icon`, `count` (default 1),
`optional`, `hidden`, `when` (a condition that must hold for progress to count).

| Type | Fields | Driven by |
|------|--------|-----------|
| `solace:kill` | `entity` id or `#tag`, optional `name` (contains), `nbt` | `LivingDeathEvent` |
| `solace:break_block` / `solace:place_block` | `block` id or `#tag` | `BlockEvent.BreakEvent` / `EntityPlaceEvent` |
| `solace:interact_block` | `block`, optional `pos` | `PlayerInteractEvent.RightClickBlock` |
| `solace:interact_entity` | `entity`, optional `name`, `nbt` | `PlayerInteractEvent.EntityInteract` |
| `solace:item` | `item` id or `#tag`, `consume` (bool), optional `nbt` | inventory change (container listener), debounced |
| `solace:deliver` | `item`, `npc` binding id | dialogue action / entity interact while holding |
| `solace:craft` / `solace:smelt` | `item` | `ItemCraftedEvent` / `ItemSmeltedEvent` |
| `solace:reach_location` | `dimension`, `pos`, `radius` | position timer (below) |
| `solace:find_structure` | `structure` id or `#tag` | position timer + cached structure-start lookup |
| `solace:enter_dimension` | `dimension` | `PlayerChangedDimensionEvent` |
| `solace:enter_biome` | `biome` id or `#tag` | position timer |
| `solace:dialogue_node` | `dialogue`, `node` | dialogue runtime |
| `solace:use_item` | `item` | `LivingEntityUseItemEvent.Finish` + right-click |
| `solace:advancement` | `advancement` | `AdvancementEarnEvent` |
| `solace:reputation` | `faction`, `min` | reputation change |
| `solace:faction_rank` | `faction`, `rank` | reputation change |
| `solace:condition` | `when` (any condition: flags, variables…) | re-check on state change |
| `solace:checkmark` | — | client intent, server-checked |
| `solace:custom` | `handler` id, free-form `data` | Java `ObjectiveTypes.register` / KubeJS |

Phase 2 implements `kill`, `break_block`, `place_block`, `item`, `reach_location`, `condition`,
`checkmark`. The rest land in phase 7 (dialogue/reputation ones with their own phases).

**Position timer.** One server tick handler visits a rotating slice of online players so each
player is examined every 20 ticks, and only if they hold an active location/biome/structure
objective (a per-player counter). Structure checks call
`StructureManager.getStructureWithPieceAt` for the player's block position and cache the answer
per `(dimension, chunk)` in a bounded LRU map.

### 5.4 Conditions

`{"type": …}` objects. Used by chapter `unlock`, quest `visible_when`, objective `when`,
transition `when`, dialogue choice `visible_when`/`enabled_when`, text variants, NPC bindings and
markers.

| Type | Fields |
|------|--------|
| `solace:all` / `solace:any` / `solace:not` | `of` (list) / `of` (list) / `condition` |
| `solace:quest_state` | `quest`, `state`: `locked`, `available`, `active`, `completed`, `failed`, `claimable` |
| `solace:phase` | `quest`, `phase`, optional `reached: true` (ever been in it) |
| `solace:objectives_complete` | optional `objectives` (ids); default = all non-optional in the current phase |
| `solace:flag` | `flag`, `value` (default true) |
| `solace:variable` | `name`, `op`: `==` `!=` `<` `<=` `>` `>=`, `value` |
| `solace:reputation` | `faction`, `op`, `value` |
| `solace:faction_rank` | `faction`, `op`, `rank` |
| `solace:has_item` | `item` or `#tag`, `count`, `held` (bool) |
| `solace:dimension` | `dimension` |
| `solace:time` | `from`, `to` (day ticks 0–23999, may wrap) |
| `solace:entity` | `name_contains`, `nbt`, `profession` — only valid where an entity is in context (bindings, kill/interact objectives) |
| `solace:predicate` | `predicate` — a vanilla `data/<ns>/predicates` id, evaluated with the player as `this` |
| `solace:custom` | `handler`, `data` |

Conditions are pure functions of `(player, owner state, optional entity)` and never mutate.

### 5.5 Rewards

Share `id` and optional `text`/`icon`.

| Type | Fields |
|------|--------|
| `solace:item` | `item`, `count`, optional `nbt` (overflow drops at the player's feet) |
| `solace:xp` | `points` or `levels` |
| `solace:loot_table` | `table` |
| `solace:command` | `command`, `permission_level` (default 2) |
| `solace:flag` / `solace:variable` | `flag`,`value` / `name`, `op`: `set` `add`, `value` |
| `solace:reputation` | `faction`, `amount` |
| `solace:unlock` | `quest` or `chapter` |
| `solace:dialogue` | `dialogue` (opens after claim) |
| `solace:custom` | `handler`, `data` |

Commands run from the **server** command source positioned at the player, with output
suppressed. The player is referenced by the literal token `@p` in the command; it is replaced by
the player's UUID string (a valid entity selector argument), never by the display name, so a
crafted player name cannot inject command text. Commands only ever come from server-loaded
content.

Claiming: manual from the details panel, or automatic with `auto_claim`. The claimed set is
stored per owner per quest completion; a claim marks the reward claimed **before** granting it.

### 5.6 Dialogue — `dialogues/cleric/first_steps.json`

```json
{
  "start": [
    { "node": "report", "when": { "type": "solace:phase", "quest": "astralis:village/first_steps", "phase": "report" } },
    { "node": "busy", "when": { "type": "solace:quest_state", "quest": "astralis:village/first_steps", "state": "active" } },
    { "node": "greet" }
  ],
  "nodes": {
    "greet": {
      "speaker": "Marrow",
      "portrait": { "entity": true },
      "text": "Hm? Oh. A new face. The readings said nothing about a new face.",
      "choices": [
        { "text": "Can I help with anything?", "to": "offer" },
        { "text": "Just passing through.", "to": "@close" }
      ]
    },
    "offer": {
      "text": [
        { "when": { "type": "solace:faction_rank", "faction": "astralis:villagers", "op": ">=", "rank": "friendly" },
          "text": "For you? Gladly. The dead are restless by the old mill." },
        { "text": "If you must. The dead are restless by the old mill." }
      ],
      "choices": [
        { "text": "I'll deal with them.",
          "actions": [ { "type": "solace:start_quest", "quest": "astralis:village/first_steps" } ],
          "to": "@close" },
        { "text": "Not today.", "to": "@close", "cooldown_seconds": 60 }
      ]
    },
    "busy": { "text": "Three, I said. Come back when it is done.", "auto_advance_seconds": 4, "to": "@close" },
    "report": {
      "text": "Quiet again? Good. Good.",
      "to": "report_done"
    },
    "report_done": {
      "text": "Take this. You have earned a place at the fire.",
      "actions": [ { "type": "solace:give_item", "item": "minecraft:candle", "count": 2 } ],
      "to": "@close"
    }
  }
}
```

- `start` is an ordered list; the first entry whose `when` passes is the entry node. This is how
  one NPC shows "not started / in progress / finished" lines (the old Blabber
  `*_unfinished` / `*_finished` files collapse into one dialogue).
- A node has `choices` **or** a single `to` (continue). `@close` ends the session.
- Node fields: `speaker`, `portrait` (`{"entity": true}`, `{"item": id}`, `{"texture": id}`),
  `text` (component or ordered variant list), `actions`, `choices`, `to`,
  `auto_advance_seconds`, `cooldown_seconds`.
- Choice fields: `text`, `to`, `actions`, `visible_when`, `enabled_when` (shown greyed with
  `disabled_text`), `cooldown_seconds`.
- A `solace:dialogue_node` objective completes when the node is **entered**.

**Actions** (also usable on phase `on_enter`/`on_exit` and transitions):

`solace:start_quest`, `solace:set_phase`, `solace:complete_objective`, `solace:complete_quest`,
`solace:fail_quest`, `solace:give_item`, `solace:take_item` (fails the choice if missing unless
`optional`), `solace:set_flag`, `solace:set_variable`, `solace:reputation`, `solace:add_marker`,
`solace:remove_marker`, `solace:command`, `solace:open_dialogue`, `solace:close`,
`solace:custom`. `solace:open_shop` is reserved (v1.x).

### 5.7 NPC binding — `npcs/cleric.json`

```json
{
  "entity": "minecraft:villager",
  "when": {
    "type": "solace:all",
    "of": [
      { "type": "solace:entity", "name_contains": "Clerc" },
      { "type": "solace:entity", "profession": "minecraft:cleric" }
    ]
  },
  "dialogue": "astralis:cleric/first_steps",
  "priority": 10,
  "freeze": true,
  "max_distance": 6
}
```

Bindings are indexed by entity type. On `PlayerInteractEvent.EntityInteract` the matcher walks
that type's bindings by descending priority; the first whose `when` passes opens the dialogue
and cancels the event. No match → the event is left untouched, so ordinary villagers still
trade. A binding may list several `dialogues` with their own `when` for NPCs that switch
conversations by story state. The binding id (`astralis:cleric`) is what `deliver` objectives and
`npc` markers refer to.

### 5.8 Faction — `factions/villagers.json`

```json
{
  "name": "Villagers",
  "color": "#7FB069",
  "icon": { "item": "minecraft:emerald" },
  "default": 0,
  "min": -200, "max": 200,
  "ranks": [
    { "id": "hated",    "name": "Hated",    "at": -200 },
    { "id": "hostile",  "name": "Hostile",  "at": -100 },
    { "id": "neutral",  "name": "Neutral",  "at": -14 },
    { "id": "friendly", "name": "Friendly", "at": 15 },
    { "id": "honoured", "name": "Honoured", "at": 75 },
    { "id": "exalted",  "name": "Exalted",  "at": 150 }
  ],
  "relations": [ { "faction": "astralis:illagers", "factor": -0.5 } ],
  "source": { "type": "solace:villager_gossip", "radius": 48 }
}
```

- A rank applies from its `at` value upward. Relations propagate one step only (no chains), so a
  gain of 10 here is −5 with the illagers and stops there.
- Without `source`, standing is a stored integer per owner. With
  `solace:villager_gossip` it is read from the world (§9) and nothing is stored but the last seen
  value.

### 5.9 Marker — inline or `markers/old_mill.json`

```json
{ "target": "position", "dimension": "minecraft:overworld", "pos": [212, 71, -340],
  "label": "Old mill", "color": "gold", "beam": false }
```

`target`: `position`; `structure` (`structure` id or `#tag`, nearest, resolved once then cached
for that owner); `npc` (binding id — last known position of the entity the player talked to, or
the nearest loaded match); `objective` (derives from a location/structure objective). Anywhere a
marker is accepted, a string is a reference to a `markers/` file and an object is inline.

---

## 6. Runtime

### 6.1 Quest engine

`QuestEngine` is the single writer of player state, always on the server thread.
`start`, `progress`, `setPhase`, `complete`, `fail`, `reset`, `claim`, `setFlag`, `setVariable`,
`addReputation`. Each mutation: validate → apply → append to history → re-evaluate transitions
for affected quests (bounded to 32 chained transitions per call to stop content loops) → queue a
delta for the owner's online players → fire the Forge/KubeJS event.

### 6.2 Objective index

**As built:** the index is per owner, not global: `type → active objectives of this owner's current
phases`, rebuilt lazily after a state change or reload, plus a global set of objective types in
use for an early exit. Tags are tested live (`is(tag)`), so there is nothing to expand or rebuild
on tag reload. An event costs one set lookup when no quest uses its type, otherwise a walk over
that player's few active objectives of the type. The paragraphs below describe the original plan.

Built at reload and on `TagsUpdatedEvent`:
`Map<ObjectiveType, Map<ResourceLocation target, List<ObjectiveRef>>>`, with tags expanded to
their members and a wildcard bucket for objectives that match on name/NBT only.

An event handler does: look up `(type, target)` → for each ref, is that quest active for this
owner and is it the current phase? (one hash lookup in the owner's active set) → evaluate the
objective's extra filters → `QuestEngine.progress`. Cost is proportional to the objectives that
could match this exact event, not to players × quests. Handlers return immediately when the index
has no bucket for the event's type.

### 6.3 Dialogue sessions

- One `DialogueSession` per player: `{sessionId (random int), dialogue, node, npc UUID, npc
  position, sequence}`. Two players on the same NPC have two independent sessions.
- The server sends the resolved node (speaker, chosen text variant, the visible choices with
  their original indices). The client never evaluates conditions.
- The NPC is frozen while its **session count > 0**: navigation stopped and look-at-player
  applied by a short-lived goal/tick hook keyed by entity UUID. The count is decremented on
  close, logout, death, dimension change, distance > `max_distance`, NPC removal, reload and
  server stop, and a 5 s watchdog releases any entity whose sessions are all gone.
- Sessions end on content reload (the node may no longer exist).

---

## 7. Persistence

### 7.1 Storage

`QuestSavedData extends SavedData`, stored once per world in the overworld data folder as
`solace_quests.dat`:

```
{ schema: 1,
  owners: { <ownerId>: PlayerQuestData },
  members: { <playerUUID>: <ownerId> } }      // v1: identity mapping, omitted
```

`SavedData` rather than a player capability because it survives death/clone without copy
handlers, can be edited for offline players by admin commands, and makes party sharing a mapping
change instead of a data move.

### 7.2 `PlayerQuestData`

- `quests: id → { status, phase, progress: {objectiveId → int}, startedAt, deadline,
  completions, cooldownUntil, claimed: [rewardId], history: [{time, phase, via}], orphaned }`
- `flags: Set<String>`, `variables: Map<String,int>`
- `reputation: Map<factionId,int>`
- `cooldowns: Map<"dialogue#node[/choice]", gameTime>`
- `story: bounded list (500) of {time, quest|dialogue, textKey}` for the journal
- `tracked: [questId]` (max 5), `markers: [dynamic markers added by actions]`

`history` is the branch path the player took and is what the journal's "story so far" and
`/solacequests debug` print.

### 7.3 Party progress (v2)

All engine and condition code takes a `ProgressOwner`, never a raw player. In v1
`ProgressOwner.of(player)` is the player's UUID. v2 adds a Solace party (FTB Teams is gone and is
not a dependency) and points `members` at a shared owner id; rewards stay per player through the
`claimed` set gaining a player key. No format break: schema 1 → 2 migration adds the map.

### 7.4 Versioning and orphans

`schema` is read first; `QuestDataMigrations` holds `int → Function<CompoundTag, CompoundTag>`
steps applied in order. A file with a **newer** schema than the mod is not loaded and not
overwritten (the save is refused with a loud log line) so a downgrade cannot destroy progress.

Content reloads never delete state. Quests, phases, objectives or factions whose ids disappeared
are kept and marked `orphaned`; they are hidden from the book, listed by
`/solacequests debug`, and come back to life if the id returns. A quest whose current phase id
vanished is moved to its first phase and the fact is logged.

---

## 8. Networking

**As built (phase 2):** no packets yet — there is no client UI to feed. Feedback is chat lines, and
the two player intents that exist are commands re-checked on the server: `/sq claim <quest>` and
`/sq check <quest> <objective>`. The channel below arrives with the dialogue screen (phase 3) and
the book (phase 4).

Channel `solace:quests`, `SimpleChannel`, protocol string `"1"`, same shape as
`LootEditorNetwork`. Bump the string on any wire change.

### 8.1 Server → client

| Packet | Content |
|--------|---------|
| `ContentSync` | The definitions this player may see (chapters, quests, factions; text as components), gzip-compressed, split into ≤ 30 KiB parts. Sent on login and reload; single quests are re-sent when they become visible. Dialogue trees and NPC bindings are **not** synced. |
| `StateSnapshot` | The owner's quest states, progress, flags needed by the UI, reputation, tracked list. Sent on login, reload, respawn and dimension change. |
| `StateDelta` | Changed quest entries / reputation values only. Batched once per tick per player. |
| `DialogueNode` | `sessionId`, `sequence`, speaker, text, portrait, visible choices (index + text + enabled). |
| `DialogueClose` | `sessionId`. |
| `Toast` | kind (started, phase, completed, failed, reputation), title, subtitle, icon. |
| `Markers` | Full marker list for the player: id, dimension, pos, label, colour, beam. Sent on change and on a 2 s refresh for moving (`npc`) targets. |

### 8.2 Client → server (intents)

| Packet | Server re-checks |
|--------|------------------|
| `ClaimReward(quest, reward \| all)` | quest known, completed, reward exists, not already claimed. |
| `TrackQuest(quest, on)` | quest active and visible; list size ≤ 5. |
| `Checkmark(quest, objective)` | objective is `solace:checkmark`, in the current phase, `when` passes. |
| `DialogueChoice(sessionId, sequence, index)` | session belongs to sender, `sequence` is current, index is one of the choices sent, its conditions and cooldown still pass, player within `max_distance` of the NPC and in the same dimension. |
| `DialogueAdvance(sessionId, sequence)` | same, and the node has a `to`. |
| `DialogueClose(sessionId)` | session belongs to sender. |
| `AbandonQuest(quest)` | quest active and `abandonable`. |

Opening the book or journal is local (the data is already mirrored) and sends nothing.

Hardening: every string id is read with a 256-char cap and must parse as a `ResourceLocation`;
indices are bounds-checked; a per-player token bucket (20 intents burst, 10/s refill) drops and
counts excess packets, and a player far over the limit is logged once per minute. The client
never sends progress, item stacks, text or commands. Handlers run on the main thread
(`consumerMainThread`) and always `setPacketHandled(true)`.

### 8.3 Lifecycle

- **Login**: `ContentSync`, `StateSnapshot`, `Markers`.
- **Death / respawn / dimension change**: dialogue session closed, NPC released; state is in
  `SavedData` so nothing is copied; snapshot + markers re-sent.
- **Logout**: session closed, rate-limit bucket dropped, client clears its Xaero origin.
- **Server restart**: state from `SavedData`; sessions are not persisted.
- **Reload**: sessions closed, index rebuilt, full re-sync.

---

## 9. Reputation, Thief and vanilla gossip

Verified against `mods/thief-1.20.1-1.2.2-forge.jar`:

- `io.github.mortuusars.thief.forge.api.event.ReputationLevelChangedEvent` — public final fields
  `LivingEntity criminal`, `Villager villager`, `Reputation oldReputation`, `newReputation`.
- `…CrimeCommitedEvent` — `LivingEntity criminal`, `Crime crime`, `List<LivingEntity> witnesses`.
- `…GiftGivenEvent` — `ServerPlayer player`, `Villager villager`, `ItemStack gift`.
- `io.github.mortuusars.thief.world.Reputation` — enum `HATED, UNWELCOME, DISTRUSTED, NEUTRAL,
  ACCEPTED, RESPECTED, HONORED`, with static `fromValue(int)`,
  `averageValueFromVillagers(LivingEntity, List<Villager>)`, `valueOf(LivingEntity, LivingEntity)`.

Design:

- **Read**: a `solace:villager_gossip` faction's value is the average of vanilla
  `Villager.getPlayerReputation(player)` over villagers within `radius`. This is plain vanilla
  gossip, so it works with or without Thief, and Thief's crimes and gifts are already in it.
  It is recomputed when one of the Thief events fires for that player, when a quest writes
  gossip, and on the 20-tick position timer while villagers are nearby; the last value is cached
  for use away from villages.
- **Write**: `solace:reputation` on a gossip faction adds vanilla gossip to the villagers in
  radius — positive amounts as `MINOR_POSITIVE` (and `MAJOR_POSITIVE` above a threshold),
  negative as `MINOR_NEGATIVE` / `MAJOR_NEGATIVE` — so trade prices and Thief react exactly as if
  the player had earned it. Amounts are clamped to vanilla's per-type maxima.
- **Events** (`ThiefCompat`, loaded only if `thief` is present): `ReputationLevelChangedEvent` →
  refresh the faction, progress `solace:reputation` / `solace:faction_rank` objectives, toast.
  `CrimeCommitedEvent` / `GiftGivenEvent` → refresh only. Without Thief the timer refresh covers
  the same objectives, slightly later.
- Rank names on a gossip faction are the pack author's; the sample pack mirrors Thief's seven
  levels so the journal and Thief's tooltip agree.

Stored factions are ordinary integers changed by rewards/actions/commands, with one-step
relation propagation (§5.8).

---

## 10. Markers and Xaero

Server side, markers are data: the union of markers from active quests' current phases, tracked
objectives and dialogue actions, resolved to `(dimension, pos)`. They are recomputed on quest
state change and on a 2 s timer for moving targets; never per tick, never per frame.

Client side:

- **Xaero's Minimap 26.6.0** (verified in the jar): the session is
  `xaero.hud.minimap.BuiltInHudModules.MINIMAP.getCurrentSession()` (a `MinimapSession`);
  `getWorldManager().getCustomWaypoints(ResourceLocation origin)` returns an
  `Int2ObjectMap<Waypoint>` of non-persistent waypoints owned by that origin. Solace uses origin
  `solace:quests`, fills it with `new Waypoint(x, y, z, name, symbol, WaypointColor, …)` marked
  temporary, and clears it on logout / marker removal. The jar also has a newer
  `waypoint.thirdparty.ThirdPartyWaypoints` (`add(String id, Waypoint)`, `remove(String)`,
  `clear()`), which fits even better; which of the two this version actually renders is the first
  thing to confirm in phase 6. Either way the player's own waypoint sets and files are never
  touched.
- **Xaero's World Map 1.47.0** draws the minimap's waypoints through its own
  `SupportXaeroMinimap` bridge, so no separate world-map code is expected; to be confirmed in
  game in phase 6.
- **Fallback (always available, also with Xaero if enabled in config)**: the HUD tracker shows an
  arrow + distance for the tracked quest's nearest marker, and markers with `"beam": true` render
  a thin in-world beam within render distance.

All Xaero calls live in `client.quests.XaeroMarkerCompat`, referenced only behind
`ModList.get().isLoaded("xaerominimap")`.

---

## 11. Client UI

- **Quest book** (`QuestBookScreen`, keybind + optional item `solace:quest_book`): left column of
  chapters (`SolaceChoiceCard`), central pan/zoom canvas with nodes at `position` on a grid and
  dependency lines, node ring colour by state (locked `MUTED`, available `TEXT`, active `GOLD`,
  claimable pulsing `GOLD`, complete `GOLD_DIM`). Clicking a node slides in a right-hand details
  panel: description, objectives with progress bars, rewards, Claim / Track / Abandon.
- **Journal** tab in the same screen: Active / Completed / Failed lists, story log (phase `story`
  text and dialogue lines seen), faction list with rank bars, tracked-quest selector.
- **HUD tracker**: up to 5 tracked quests, current-phase objectives with `n/N`, compass arrow.
  Position and scale in the client config, toggle keybind. Drawn through
  `RegisterGuiOverlaysEvent`.
- **Toasts**: quest started, phase changed, completed, failed, rank changed.
- **Dialogue** (`DialogueScreen`): letterboxed bottom panel in `PANEL` with a `GOLD_DIM` rule,
  speaker name plate in `GOLD`, typewriter text (click/space completes the line, again advances),
  choices stacked on the right as `SolaceChoiceCard`s with number-key shortcuts, optional entity
  or item portrait on the left. Does not pause the game. Esc sends `DialogueClose`.

Config: `config/solace-quests.json` (server/common: limits, editor enable, timers) and
`config/solace-quests-client.json` (HUD position, toasts, compass with Xaero), using the existing
Gson + atomic-write pattern from `LootEditorConfig`.

---

## 12. Commands

`/solacequests` (permission level 2), alias `/sq`:

```
reload | validate | list [chapters|quests|dialogues|factions]
quest start|complete|fail|reset <player> <quest>
quest phase <player> <quest> <phase>
quest progress <player> <quest> <objective> <value>
dialogue open <player> <dialogue> [node]
flag set|clear <player> <flag>
var set|add <player> <name> <value>
rep get|set|add <player> <faction> [value]
debug <player> [quest]
editor            (opens/prints the editor link; see §13)
```

All ids have suggestions from the loaded content.

---

## 13. Web editor

Location: `Solace/editor/`. Build output `editor/dist/index.html` (one self-contained file),
copied into the jar at `assets/solace/quest_editor/index.html` by `processResources` when
present.

### 13.1 Server

- Started only by `/solacequests editor`, only for a sender with permission level ≥ 2, and only
  on an integrated (singleplayer/LAN) server unless `editor.allowDedicated` is set in config
  (default false). Stops on server stop.
- Binds `127.0.0.1` on an ephemeral port. A fresh 128-bit random token per start. The command
  replies with a clickable link `http://127.0.0.1:<port>/#token=<token>` — the token is in the
  URL fragment, so it is never sent in a request line, never logged by the server, and never in a
  `Referer`.
- Every `/api/*` request must carry `Authorization: Bearer <token>` (constant-time compare), a
  `Host` header equal to `127.0.0.1:<port>` (blocks DNS rebinding) and, if present, an `Origin`
  equal to the same. No CORS headers are sent.
- Work that touches the game is queued onto the server thread.

### 13.2 API

| Method + path | Purpose |
|---------------|---------|
| `GET /` | the editor page |
| `GET /api/session` | pack info, namespaces, protocol version |
| `GET /api/registries/{items,blocks,entities,structures,biomes,sounds,dimensions,advancements,professions,tags}` | id lists for autocomplete |
| `GET /api/content` | every loaded entry: kind, id, source (`datapack`/`loose`), JSON |
| `PUT /api/content/{kind}/{namespace}/{path}` | validate, then write the loose file (atomic temp + move) |
| `DELETE /api/content/{kind}/{namespace}/{path}` | delete the loose file, or write a `removed` tombstone over a datapack entry |
| `POST /api/validate` | validate a body without saving |
| `POST /api/reload` | hot reload, returns the validation report |

Writes are confined to `config/solace/quests/`: namespace and path are validated as a
`ResourceLocation`, the resolved path is normalised and must stay under the root, bodies are
capped at 512 KiB. The editor never writes into datapacks.

### 13.3 App

Dark theme in the Solace palette, system font stack, 16 px base. Three regions at most: a
collapsible left navigator (chapters, dialogues, NPCs, factions, markers), a large central canvas
(quest graph or dialogue node graph: drag nodes, drag from a node's edge to draw a
dependency/choice link), and a collapsible right inspector whose forms show the essential fields
first with "Advanced" folded. A bottom drawer holds the validation panel; clicking a problem
jumps to the field. Registry-backed fields autocomplete when online.

**Offline**: the same `index.html` opened from disk. Without a token it uses the browser's
directory picker to open a `solace_quests` or `config/solace/quests` folder (falling back to
import/export of single files where the picker is unavailable), validates structure client-side,
and has no registry autocomplete or reload.

---

## 14. KubeJS

`compileOnly` against `kubejs-forge-2001.6.5-build.16.jar` and `rhino`. A `KubeJSPlugin` is
listed in a `kubejs.plugins.txt` resource, which KubeJS itself reads — nothing in Solace touches
those classes when KubeJS is absent.

- Event group `SolaceQuestEvents`: `questStarted`, `questCompleted`, `questFailed`,
  `phaseChanged`, `objectiveProgressed`, `dialogueChoice`, `reputationChanged`. The same moments
  are plain Forge events (`com.solace.quests.api.event.*`) for Java mods.
- Binding `SolaceQuests`: `registerObjective(id, handler)`, `registerCondition(id, fn)`,
  `registerReward(id, fn)`, `registerAction(id, fn)` — these back the `solace:custom` types — and
  `progress(player, quest, objective, amount)`, `setFlag`, `getVariable`… for scripts.

---

## 15. Testing

- Unit tests (see §1 decision 1) cover JSON parsing, validation messages with paths, the
  per-file fallback, condition evaluation, objective matching/index lookups, transition
  ordering, reputation propagation and state (de)serialisation + migration. Classes under test
  take plain ids and small interfaces so tests do not need a Minecraft bootstrap.
- `./gradlew build` in `Solace/` must pass at the end of every phase.
- Each phase ends with an in-game checklist and a sample pack under
  `config/paxi/datapacks/astralis/data/astralis/solace_quests/` (parent repo — only created when
  you say so; until then samples live in `Solace/docs/quests/samples/`).

---

## 16. Delivery plan

| Phase | Contents | Status |
|-------|----------|--------|
| 1 | This document | done |
| 2 | Loader, validation, hot reload, conditions, flags/variables, `QuestSavedData`, engine, commands, objectives (kill, break, place, item, reach location, condition, checkmark), rewards | done — no sync yet (see §8); time limits, NBT matching and the `orphaned` flag being stored (it is derived instead) are not in |
| 3 | Dialogue runtime, NPC bindings, `DialogueScreen`, actions | |
| 4 | Quest book, journal, HUD tracker, toasts | |
| 5 | Factions, reputation, gossip bridge, Thief events | |
| 6 | Markers, Xaero bridge, compass/beam fallback | |
| 7 | Remaining objectives, time limits, KubeJS, polish, JEI/EMI if time | |
| 8 | Web editor (can start once §5 is frozen after phase 3) | |
| 9 | Blabber migration script for the 41 files in `…/data/astralis/blabber/dialogues/` | |

Documentation written as the code lands: `AUTHORING.md`, `EDITOR.md`, `KUBEJS.md`,
`TROUBLESHOOTING.md` in this folder.
