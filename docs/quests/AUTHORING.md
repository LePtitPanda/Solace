# Solace Quests — Authoring guide

Every content file, field, objective, condition and effect, with examples. Working content:
[samples/astralis](samples/astralis). A deliberately invalid file: [samples/broken](samples/broken).

Related: [EDITOR.md](EDITOR.md) (web editor), [KUBEJS.md](KUBEJS.md) (scripting),
[TROUBLESHOOTING.md](TROUBLESHOOTING.md) (what each validation message means),
[DESIGN.md](DESIGN.md) (how it works inside).

## Where files go

| Location | Id of `…/quests/village/first_steps.json` |
|----------|--------------------------------------------|
| Datapack: `data/<namespace>/solace_quests/<kind>/…` | `<namespace>:village/first_steps` |
| Loose: `config/solace/quests/<namespace>/<kind>/…` | `<namespace>:village/first_steps` |

`<kind>` is one of `chapters`, `quests`, `dialogues`, `npcs`, `factions`, `markers`, `loot`,
`recipe_locks`, `trades`. The id always
comes from the location; an `id` field is ignored with a warning.

**Loose files win** over a datapack file with the same id. A loose file containing only
`{"removed": true}` hides the datapack entry. The web editor only ever writes loose files.

Apply changes with `/sq reload` (quests only) or `/reload` (all datapacks). Limits: 512 KiB per
file, 32 MiB in total, 20 000 files, 64 levels of JSON nesting.

## Text

Any text field takes vanilla text-component JSON: `"Plain words"`,
`{"translate": "astralis.quest.title"}`, or `{"text": "Gold", "color": "gold"}`.

## Icons

`"icon": {"item": "minecraft:bell"}` or `"icon": {"texture": "astralis:textures/gui/quest.png"}`
(a 16×16 texture).

## Chapter — `chapters/*.json`

```json
{ "title": "The Wardens", "description": "…", "icon": { "item": "minecraft:shield" }, "order": 20,
  "unlock": { "type": "solace:quest_state", "quest": "astralis:village/first_steps", "state": "completed" },
  "hidden_until_unlocked": true }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `title` | required | Text. |
| `description` | none | Text, shown under the chapter list. |
| `icon` | none | See *Icons*. |
| `order` | 0 | Sort position in the book. |
| `unlock` | none | Condition; while false every quest in the chapter is locked. |
| `hidden_until_unlocked` | false | The chapter and its quests are not sent to the client until unlocked. |

## Quest — `quests/**/*.json`

```json
{
  "chapter": "astralis:village",
  "title": "Supplies",
  "icon": { "item": "minecraft:oak_log" },
  "position": { "x": 2, "y": 0 },
  "requires": { "mode": "all", "quests": ["astralis:village/first_steps"] },
  "objectives": [
    { "id": "logs", "type": "solace:item", "item": "#minecraft:logs", "count": 8, "consume": true }
  ],
  "rewards": [ { "id": "xp", "type": "solace:xp", "levels": 2 } ]
}
```

| Field | Default | Meaning |
|-------|---------|---------|
| `chapter` | required | Chapter id. Must exist. |
| `title` | required | Text. |
| `description` | none | Text. |
| `icon` | none | Shown on the quest's node in the book. |
| `position` | 0,0 | `{"x": n, "y": n}` grid cell in the chapter's graph. |
| `requires.quests` | none | Quest ids that must have been completed. Drawn as lines in the book. |
| `requires.mode` | `"all"` | `"all"`, `"any"`, or `{"at_least": N}`. |
| `optional` | false | Not counted in the chapter's done/total. |
| `hidden_until_unlocked` | false | Not sent to the client while locked. |
| `visible_when` | none | Condition; the quest is not sent to the client while false. |
| `hidden_until` | `none` | Keeps the quest out of the book until enough is done: `one_dependency`, `all_dependencies`, `n_dependencies` (required quests completed) or `one_objective`, `all_objectives`, `n_objectives` (its own objectives done; use with `auto_start`). While hidden it shows no toasts either. |
| `hidden_count` | 1 | The N for `n_dependencies` / `n_objectives`. |
| `auto_start` | false | Starts by itself the first time it becomes available. A repeatable quest does not restart itself. |
| `auto_claim` | false | Rewards are handed out on completion instead of on claim. Without it only item, XP and loot rewards wait for "Claim rewards"; flags, reputation and other bookkeeping rewards always apply on completion. |
| `repeatable` | off | `{"cooldown_seconds": 60}` — can be started again after the cooldown. Progress and claimed rewards reset on restart; unclaimed rewards must be claimed first. |
| `time_limit_seconds` | 0 | Above 0: the quest fails when the time is up. |
| `on_fail` | `"keep"` | `"reset"`: a failed quest becomes available again. |
| `abandonable` | true | The player may abandon it from the book. |
| `markers` | none | Markers shown while the quest is active (any phase). See *Markers*. |
| `phases` | — | List of phases (below). |
| `objectives`, `transitions`, `story`, `on_enter`, `markers` | — | Shorthand for a single phase called `main`, instead of `phases`. |
| `rewards` | none | List of effects with an `id`. See *Effects*. |

### Phase

```json
{ "id": "report", "story": "Time to report back.",
  "objectives": [ { "id": "talk", "type": "solace:dialogue_node", "dialogue": "astralis:cleric/first_steps", "node": "report_done" } ],
  "markers": [ { "target": "npc", "npc": "astralis:cleric" } ],
  "on_enter": [ { "type": "solace:set_flag", "flag": "reporting" } ],
  "transitions": [
    { "to": "@fail", "when": { "type": "solace:flag", "flag": "betrayed" }, "label": "betrayal" },
    { "to": "@complete", "when": { "type": "solace:objectives_complete" } }
  ] }
```

- `id` is required and unique in the quest. `story` is shown in chat when the phase begins and
  kept in the journal.
- `on_enter`: effects run when the phase begins.
- `transitions` are checked in order; the first whose `when` is true wins. `to` is a phase id,
  `@complete` or `@fail`. `label` is recorded in the player's quest history. `actions` (effects)
  run when it fires. A transition without `when` always fires.
- **No `transitions`**: when every non-optional objective is done, the quest moves to the next
  phase in the file, or completes after the last one.
- Transitions are re-checked whenever progress, a flag, a variable or reputation changes, and once
  a second for time/inventory/position conditions.
- Branching: put several transitions with different conditions, or have a dialogue choice run
  `solace:set_phase`.

### Objectives

Common fields: `id` (required, unique in the quest — saved progress is keyed by it), `type`,
`text`, `icon`, `count` (default 1), `optional` (default false), `hidden` (not shown to the
player), `when` (condition that must hold for progress to count; for kill and interact objectives
it may use `solace:entity` to inspect the entity), and:

| Field | Meaning |
|-------|---------|
| `needs` | Ids of objectives **in the same phase** that must be done for this one to progress. The book and HUD draw it indented under them and greyed out ("Paused until: …") while they are not done. Also gives an order inside one phase ("craft the camera" before "take a photo"). |
| `on_lost` | What happens to a started, unfinished objective when one of its needs stops holding (only a `solace:while` can stop holding): `keep` (default), `reset` (back to 0), `fail` (the quest fails, history label `lost:<id>`). |
| `paused_text` | Text shown instead of the default "Paused until: …". |
| `distinct` | Count each thing once: `entity_type` or `entity` (kill, interact_entity, tame, breed, shear, hatch), `item` (craft, smelt, use_item), `block` (break, place, interact_block), `structure` (find_structure: separate structures, so `count` can be above 1). |
| `nbt` | Item objectives (`item`, `deliver`, `craft`, `smelt`, `use_item`): SNBT that must be contained in the stack's tag. |

**"Kill 6 crows in the village"** — a live objective and one that needs it:

```json
"objectives": [
  { "id": "in_village", "type": "solace:while", "text": "Be in Bellhaven",
    "when": { "type": "solace:area", "area": "bellhaven" } },
  { "id": "crows", "type": "solace:kill", "entity": "alexsmobs:crow", "count": 6,
    "needs": ["in_village"], "on_lost": "keep" }
]
```

A `while` objective counts toward completion like any other, so the quest completes while the
player is in the area. Mark it `optional` if it should only gate other objectives.

| Type | Fields | Counts when |
|------|--------|-------------|
| `solace:kill` | `entity` (id or `#tag`), `name` (name contains) | the player kills a matching entity |
| `solace:break_block` | `block` (id or `#tag`) | the player breaks a matching block |
| `solace:place_block` | `block` | the player places a matching block |
| `solace:interact_block` | `block` | the player right-clicks a matching block |
| `solace:interact_entity` | `entity`, `name` | the player right-clicks a matching entity |
| `solace:item` | `item` (id or `#tag`), `consume` (default false) | the player holds `count` of the item (checked once a second). With `consume` the items are taken. Once complete it stays complete. |
| `solace:deliver` | `item`, `npc` (NPC binding id) | a dialogue choice runs the `solace:deliver` action (`npc`: the same binding id) while the player carries `count` of the item; the items are taken. Nothing is taken by the right-click itself. |
| `solace:craft` | `item` | the player crafts it (counts the stack size) |
| `solace:smelt` | `item` | the player takes it out of a furnace |
| `solace:use_item` | `item` | the player uses it (eats, drinks, right-clicks) |
| `solace:reach_location` | `dimension`, `pos` `[x,y,z]`, `radius` (default 8) | the player is within `radius` blocks |
| `solace:find_structure` | `structure` (id or `#tag`) | the player stands inside a piece of that structure |
| `solace:enter_dimension` | `dimension` | the player is in that dimension |
| `solace:enter_biome` | `biome` (id or `#tag`) | the player is in that biome |
| `solace:dialogue_node` | `dialogue`, `node` | that dialogue node is reached |
| `solace:advancement` | `advancement` | the player has the advancement |
| `solace:reputation` | `faction`, `min` | standing is at least `min` |
| `solace:faction_rank` | `faction`, `rank` | standing is at that rank or higher |
| `solace:condition` | `when` (required) | the condition becomes true |
| `solace:while` | `when` (required) | **live**: done while the condition holds, undone when it stops holding |
| `solace:group` | `of` (objective ids of this phase), `count` (default all) | `count` of the listed objectives are done ("8 of 12"). Make the children `optional` so only the group is required. |
| `solace:tame` | `entity` | the player tames it (AnimalTameEvent, or the animal belongs to the player right after a right-click — catches mods that do not fire the event) |
| `solace:breed` | `entity` | the player breeds it (counts the child) |
| `solace:shear` | `entity` | the player right-clicks it with shears while it can be sheared (any IForgeShearable) |
| `solace:hatch` | `entity` | a baby of it appears within 16 blocks of the player without being bred (egg blocks, thrown eggs, mod eggs) |
| `solace:checkmark` | — | the player clicks it in the quest book (or `/sq check <quest> <objective>`) |
| `solace:custom` | — | a script or mod calls `SolaceQuests.progress(...)`; see [KUBEJS.md](KUBEJS.md) |

Example with an entity check — only zombies with a custom-data tag count:

```json
{ "id": "marked", "type": "solace:kill", "entity": "minecraft:zombie", "count": 5,
  "when": { "type": "solace:entity", "nbt": "{Tags:[\"marked\"]}" } }
```

## Conditions

Used by chapter `unlock`, quest `visible_when`, objective `when`, transition `when`, dialogue
`start`, text variants, choice `visible_when` / `enabled_when`, and NPC bindings.

| Type | Fields |
|------|--------|
| `solace:all`, `solace:any` | `of`: list of conditions |
| `solace:not` | `condition` |
| `solace:quest_state` | `quest`, `state`: `locked`, `available`, `active`, `claimable`, `completed` (completed at least once), `failed` |
| `solace:phase` | `quest`, `phase`, `reached` (default false: must be the current phase; true: was ever entered) |
| `solace:objectives_complete` | `objectives` (ids; default all non-optional). Only meaningful inside a quest. |
| `solace:flag` | `flag`, `value` (default true) |
| `solace:variable` | `name`, `op` (`==` `!=` `<` `<=` `>` `>=`, default `==`), `value` |
| `solace:reputation` | `faction`, `op` (default `>=`), `value` |
| `solace:faction_rank` | `faction`, `op` (default `>=`), `rank` — compares rank positions |
| `solace:has_item` | `item` (id or `#tag`), `count` (default 1), `held` (default false: anywhere in the inventory), `nbt` |
| `solace:area` | `area`: the player stands inside that named area (see *Areas*); false while it is not set |
| `solace:near_npc` | `npc` (binding id), `radius` (default 8): an entity of that binding (its bound entity, for `first_met`) is that close |
| `solace:dimension` | `dimension` |
| `solace:biome` | `biome` (id or `#tag`) |
| `solace:structure` | `structure` (id or `#tag`) — the player stands inside it |
| `solace:time` | `from`, `to` in day ticks 0–23999; `from` > `to` wraps past midnight |
| `solace:advancement` | `advancement` |
| `solace:entity` | `name_contains`, `profession` (villager profession id), `nbt` (SNBT that must be contained in the entity's data). Only true where an entity is in context: NPC bindings, dialogue, kill/interact objectives. |
| `solace:predicate` | `predicate`: a vanilla `data/<ns>/predicates` id, tested on the player |
| `solace:custom` | `handler`, `data` — see [KUBEJS.md](KUBEJS.md) |

```json
{ "type": "solace:all", "of": [
    { "type": "solace:faction_rank", "faction": "astralis:wardens", "rank": "friendly" },
    { "type": "solace:not", "condition": { "type": "solace:flag", "flag": "betrayed_village" } },
    { "type": "solace:time", "from": 13000, "to": 23000 } ] }
```

## Effects (rewards and actions)

Rewards and actions are the same thing and share every type. A **reward** (in a quest's `rewards`)
needs an `id` and is claimed by the player, never twice. An **action** (dialogue node `actions`,
choice `actions`, phase `on_enter`, transition `actions`) runs immediately.

Optional on every effect: `text` and `icon` (how a reward is listed in the book). Rewards without
`text` of a bookkeeping type (flag, variable, command, unlock, custom…) are not listed, but are
still claimed with the rest.

| Type | Fields |
|------|--------|
| `solace:item` (alias `solace:give_item`) | `item`, `count` (default 1), `nbt` (SNBT string). Overflow drops at the player's feet. |
| `solace:take_item` | `item`, `count`, `optional`. On a dialogue choice: unless `optional`, the choice is refused when the player lacks the items. |
| `solace:xp` | `points` or `levels` |
| `solace:loot_table` | `table` |
| `solace:command` | `command`, `permission_level` (0–4, default 2). Runs as the server at the player's position; `@p` is replaced by the player's UUID. |
| `solace:flag` (alias `solace:set_flag`) | `flag`, `value` (default true) |
| `solace:variable` (alias `solace:set_variable`) | `name`, `op` (`set` or `add`, default `set`), `value` |
| `solace:reputation` | `faction`, `amount` (may be negative) |
| `solace:unlock` | `quest` or `chapter`: makes it available regardless of its requirements |
| `solace:start_quest` | `quest` — ignores locks, never restarts a running or finished quest |
| `solace:complete_quest`, `solace:fail_quest` | `quest` (must be active) |
| `solace:set_phase` | `quest`, `phase` |
| `solace:complete_objective` | `quest`, `objective` (must be in the current phase) |
| `solace:dialogue` (alias `solace:open_dialogue`) | `dialogue` — opens it, keeping the current NPC |
| `solace:close` | — closes the current dialogue |
| `solace:add_marker`, `solace:remove_marker` | `marker`: a `markers/` file id |
| `solace:custom` | `handler`, `data` — see [KUBEJS.md](KUBEJS.md) |
| `solace:open_structure_gate` | `structure` (id or `#tag`, must be in `config/solace-structure-gates.json`), `mode` (`once` or `infinite`, default: the config's). World-wide and idempotent; a `once` gate whose copy already generated stays closed. See [../structure-gates.md](../structure-gates.md). |
| `solace:close_structure_gate` | `structure` (id or `#tag`) |
| `solace:give_structure_map` | `structure` (id or `#tag`), `name` and `lore` (text components; `lore` a list), `decoration` (map decoration type, default `red_x`), `search_radius` (chunks, default 100). Searches off-thread; nothing found → a blank map ("the ink won't settle") that searches again when used. |
| `solace:set_area` | `area`, `radius` (default 48), `at`: `player` (default), `npc` (the dialogue NPC) or `structure` (the box of the `structure` the player stands in, plus `radius` as margin), `replace` (default false: keep an area that is already set — the first player defines it). |
| `solace:remove_area` | `area` |
| `solace:free_captive` | `into` (entity, default `minecraft:villager`), `profession` (used when there is no saved person). Runs in a dialogue with the captive: see *Captives and identity*. |
| `solace:unlock_recipes`, `solace:lock_recipes` | `lock` (a `recipe_locks/` id): sets or clears the flag `recipes:<lock>` |
| `solace:give_structure_compass` | `structure` (id or `#tag`), `item` (default `minecraft:compass`), `name`, `lore`, `sound` (optional sound id, hums while held, louder when closer), `search_radius`. Lodestone-style target in NBT; using the item searches again. |

## Dialogue — `dialogues/**/*.json`

```json
{
  "start": [
    { "node": "report", "when": { "type": "solace:phase", "quest": "astralis:village/first_steps", "phase": "report" } },
    { "node": "greet" }
  ],
  "nodes": {
    "greet": {
      "speaker": "Marrow",
      "text": "A new face.",
      "choices": [
        { "text": "Can I help?", "to": "offer" },
        { "text": "Goodbye.", "to": "@close" }
      ]
    },
    "offer": {
      "text": [
        { "when": { "type": "solace:faction_rank", "faction": "astralis:wardens", "rank": "friendly" }, "text": "For a friend? Gladly." },
        { "text": "If you must." }
      ],
      "choices": [
        { "text": "I will.", "actions": [ { "type": "solace:start_quest", "quest": "astralis:village/first_steps" } ] }
      ]
    },
    "report": { "text": "Quiet again? Good.", "to": "report_done" },
    "report_done": { "text": "Take this.", "actions": [ { "type": "solace:give_item", "item": "minecraft:candle" } ] }
  }
}
```

`start` is a node id, or an ordered list of `{node, when}`: the first entry whose condition passes
is where the conversation begins. This is how one NPC says different things before, during and
after a quest. Put the entry without `when` last.

**Node fields**

| Field | Default | Meaning |
|-------|---------|---------|
| `text` | required | Text, or a list of `{"when": condition, "text": …}` variants (first match wins; the list counts as variants when at least one entry has `when`). |
| `speaker` | the NPC's name | Text on the name plate. |
| `choices` | none | List of choices. |
| `to` | `@close` | For a node without choices: where "continue" leads. A node id or `@close`. |
| `actions` | none | Effects run when the node is entered. |
| `auto_advance_seconds` | 0 | Continue by itself after this long (nodes without choices). |
| `cooldown_seconds` | 0 | The node cannot be entered again for this long (the dialogue closes instead). |
| `portrait` | `{"entity": true}` | `{"entity": true}` draws the NPC; `{"item": id}` draws an item; `{"entity": false}` none. |
| `editor` | — | Position in the web editor. Ignored by the game. |

**Choice fields**

| Field | Default | Meaning |
|-------|---------|---------|
| `text` | required | Text. |
| `to` | `@close` | Node id or `@close`. |
| `actions` | none | Effects run when the choice is picked. |
| `visible_when` | none | Condition; the choice is not shown while false. |
| `enabled_when` | none | Condition; while false the choice is shown greyed out and cannot be picked. |
| `disabled_text` | `text` | Text shown while disabled. |
| `cooldown_seconds` | 0 | The choice disappears for this long after being picked. |

A `solace:dialogue_node` objective completes when the node is entered, after the node's actions.

## NPC binding — `npcs/*.json`

```json
{ "entity": "minecraft:villager",
  "when": { "type": "solace:entity", "name_contains": "Marrow" },
  "dialogue": "astralis:cleric/first_steps",
  "priority": 10, "freeze": true, "max_distance": 6 }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `entity` | required | Entity id or `#tag`. |
| `when` | none | Condition, normally `solace:entity`. |
| `dialogue` | — | The dialogue to open. |
| `dialogues` | — | Instead of `dialogue`: a list of `{"dialogue": id, "when": condition}`; first match wins. |
| `priority` | 0 | Higher is tried first when several bindings match. |
| `freeze` | true | The NPC stops and faces the player while talking. |
| `max_distance` | 6 | The dialogue closes if the player walks further away. |

| `bind` | `"none"` | `"first_met"`: the first matching entity the player talks to becomes **the** NPC (world-wide); no other entity matches the binding while it lives. When it dies or is removed for good, the binding is free and the next matching entity talked to takes over. |

Right-clicking an entity is only intercepted when a binding matches and a dialogue opens; any
other villager trades as usual. The binding id is what `deliver` objectives and `npc` markers use.

### Captives and identity

A `first_met` NPC carries its binding id as its **identity** (entity persistent data
`solace_identity`), and Solace keeps a snapshot of it: custom name, villager data (profession,
level), trades, XP and gossip, refreshed every time someone talks to it.

A captive placed in a structure is linked to a person with the entity tag
`"solace_identity=<binding id>"` (e.g. `Tags:["solace_identity=astralis:giver_banker"]` in the
structure NBT). Give the captive its own binding (matching that tag with `solace:entity` `nbt`)
whose dialogue has a choice running `solace:free_captive`. Freeing replaces the captive with a
villager (or `into`) that **is the same person**: the snapshot is restored, the binding moves to
it (displacing whoever took over meanwhile), and it is made persistent. Without a snapshot, the
captive's name and `profession` are used. A captive with no identity simply becomes a new villager.

The same transfer happens when another mod converts an entity with an identity (Forge
`LivingConversionEvent`): the identity moves along, and the binding follows if the new entity
matches it; a villager that comes out jobless gets the snapshot back.

## Areas

Named, world-wide columns (a box in one dimension, all heights), e.g. `bellhaven`. Set by the
`solace:set_area` effect (e.g. in the first quest: `"at": "structure", "structure": "#minecraft:village"`)
or `/sq area set <name> <radius>`; read by the `solace:area` condition.

## Quest loot — `loot/*.json`

Items added to loot as it is generated (chests when first opened — Lootr chests per player —,
mob drops, block drops), on top of the table and its modifiers.

```json
{ "tables": ["minecraft:chests/*"], "structure": "#astralis:mineshafts",
  "when": { "type": "solace:quest_state", "quest": "astralis:c1/wilds/ls2_old_crew", "state": "active" },
  "once": true, "items": [ { "item": "lets-do-quest-items:calendar", "chance": 1.0 } ] }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `items` | required | `{item, count` or `min`/`max`, `nbt`, `chance}` |
| `tables` | chests | loot table ids; an entry ending in `*` is a prefix. A rule with no `tables`, `entity` or `block` only applies to chest-type tables (path containing `chests/`), so a `structure` rule does not touch mob drops. |
| `structure` | any | the loot's origin is inside this structure (id or `#tag`) |
| `entity` | any | dropped by this entity (id or `#tag`) |
| `block` | any | dropped by this block |
| `when` | none | condition on the player who opened, killed or broke it. Loot rolled without a player never matches a rule with `when` or `once`. |
| `chance` | 1 | for the whole rule |
| `once` | false | at most once per player (flag `loot:<id>`) |

## Recipe locks — `recipe_locks/*.json`

Technique notes. A lock's recipes give no result in the crafting grid (vanilla table and
inventory, FastWorkbench) and are hidden in JEI and EMI, for each player until they learn it.

```json
{ "outputs": ["create:shaft", "create:cogwheel", "#create:gearboxes"],
  "item": "kubejs:technique_rotation", "consume_item": false,
  "learn_text": "The Mason's scrawl about rotation. You can make shafts and cogs now." }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `recipes` | — | recipe ids; an entry ending in `*` is a prefix |
| `outputs` | — | or: every recipe whose result is this item / `#tag` |
| `types` | `["minecraft:crafting"]` | recipe types the lock covers (only crafting is blocked in the grid; others are hidden in JEI/EMI only — a processing recipe has no crafting player) |
| `item` | none | right-clicking this item learns the lock (the technique note) |
| `consume_item` | false | the note is used up |
| `learn_text` | a generic line | chat message when learned |
| `unlock_when` | none | also unlocked while this condition holds |

Hidden in EMI: the recipes themselves and **every recipe that makes the same item** (Create's automatic crafting copies, sawing...). Machines (Create crafters, saws, presses) have no player, so they still process locked recipes: gate the machine itself with a lock if that matters.
Polymorph's recipe button never lists a locked recipe.

Learned = the player has the flag `recipes:<lock id>` (also set by `solace:unlock_recipes`).

## Trade locks — `trades/*.json`

```json
{ "entity": "minecraft:villager", "when": { "type": "solace:entity", "profession": "minecraft:armorer" },
  "offers": [ { "result": "minecraft:iron_chestplate" } ], "mode": "hide",
  "unlock_when": { "type": "solace:quest_state", "quest": "astralis:c1/prof/ar2", "state": "completed" } }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `entity` | `minecraft:villager` | merchant entity or `#tag` |
| `when` | none | which merchants (`solace:entity` sees the merchant) |
| `offers` | required | `"all"` or a list of `{result, cost}` (item or `#tag`; either may be left out) |
| `mode` | `disable` | `disable`: shown out of stock; `hide`: not shown |
| `unlock_when` | required | condition on the player (the merchant is in context) |

The server never fills the result slot for a locked offer, whatever the client sends.

## Faction — `factions/*.json`

```json
{ "name": "Wardens", "color": "#6FA8DC", "icon": { "item": "minecraft:shield" },
  "default": 0, "min": -100, "max": 200,
  "ranks": [
    { "id": "hated", "name": "Hated", "at": -100 },
    { "id": "neutral", "name": "Neutral", "at": 0 },
    { "id": "friendly", "name": "Friendly", "at": 50 }
  ],
  "relations": [ { "faction": "astralis:outlaws", "factor": -0.5 } ] }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `name` | required | Text. |
| `color` | gold | `"#RRGGBB"`. |
| `icon` | none | See *Icons*. |
| `default` | 0 | Starting standing. |
| `min`, `max` | −1000, 1000 | Standing is clamped to this range. |
| `ranks` | required | `{id, name, at}`. A rank applies from `at` upward. Order in the file does not matter. |
| `relations` | none | `{faction, factor}`: a change here also changes that faction by `amount × factor`. One step only; the other faction's own relations are not followed. |
| `source` | none | `{"type": "solace:villager_gossip", "radius": 48}` — see below. |

**Villager gossip factions.** With `source`, the faction is one standing per player for the whole
world, stored by Solace like any other. It moves with every first-hand vanilla gossip change —
trades, cures, hurting or killing villagers, and the **Thief** mod's crimes and gifts. One deed told
to several witnesses in the same tick counts once (the strongest villager's change); gossip that
villagers pass to each other, and gossip decay, do not move it. A `solace:reputation` effect adds
the amount to the standing and also writes real gossip to the villagers within `radius` blocks, so
their trade prices react. `/sq rep set` works on it too.

## Markers — inline, or `markers/*.json`

Anywhere a list of markers is accepted (`markers` on a quest or phase), each entry is an inline
object or the id of a `markers/` file. `solace:add_marker` / `solace:remove_marker` use file ids.

```json
{ "target": "position", "dimension": "minecraft:overworld", "pos": [212, 71, -340],
  "label": "Old mill", "color": "gold", "beam": true }
```

| `target` | Fields | Points at |
|----------|--------|-----------|
| `position` | `dimension`, `pos` | a fixed place |
| `structure` | `structure` (id or `#tag`) | the nearest one, searched once and remembered per player |
| `npc` | `npc` (binding id) | the nearest loaded match, otherwise where the player last talked to it |
| `objective` | `objective` (id of a `solace:reach_location` objective) | that objective, until it is done. Inline only. |

Common: `label` (text; default the quest title), `color` (a Minecraft colour name, default
`gold`), `beam` (default false: draw an in-world column at the marker).

Markers appear on Xaero's Minimap and World Map as temporary waypoints when Xaero is installed,
and always feed the HUD tracker's arrow and distance.

## Commands

`/solacequests` or `/sq`. Everything except `claim` and `check` needs permission level 2.

```
reload                                   hot reload quest content, print the validation report
validate                                 print the report of the last load again
list                                     chapters and their quests
quest start|complete|fail|reset <player> <quest>
quest phase <player> <quest> <phase>
quest progress <player> <quest> <objective> <value>
dialogue open <player> <dialogue> [node]
flag set|clear <player> <flag>
var set|add <player> <name> <value>
rep get|set|add <player> <faction> [value]
debug <player>                           flags, variables, reputation, every quest's state and branch path
editor                                   start the web editor link (see EDITOR.md)
area list | set <name> <radius> | remove <name>     named areas (set: around you)
npc list | unbind <binding> | bind <binding> <entity> | free <entity> [into]
claim <quest>                            (players) claim rewards
check <quest> <objective>                (players) tick a checkmark objective
```

## In game

- **Quest book**: key `J` by default (Controls → Solace). Tabs: Quests, Factions (only factions you have standing with are listed).
  Optionally also opened by right-clicking an item: set `bookItem` in
  `config/solace-quests-client.json`, e.g. `"minecraft:book"`.
- **HUD tracker**: up to 5 tracked quests. Toggle key is unbound by default. Position and other
  options are in `config/solace-quests-client.json`:
  `hud`, `hudX`, `hudY` (0–1, set by dragging in Mods → Solace → Config), `toasts`, `compass`, `xaeroWaypoints`,
  `beams`, `bookItem`.
