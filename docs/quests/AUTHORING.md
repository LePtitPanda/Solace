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

`<kind>` is one of `chapters`, `quests`, `dialogues`, `npcs`, `factions`, `markers`. The id always
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
| `auto_claim` | false | Rewards are handed out on completion instead of on claim. |
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
it may use `solace:entity` to inspect the entity).

| Type | Fields | Counts when |
|------|--------|-------------|
| `solace:kill` | `entity` (id or `#tag`), `name` (name contains) | the player kills a matching entity |
| `solace:break_block` | `block` (id or `#tag`) | the player breaks a matching block |
| `solace:place_block` | `block` | the player places a matching block |
| `solace:interact_block` | `block` | the player right-clicks a matching block |
| `solace:interact_entity` | `entity`, `name` | the player right-clicks a matching entity |
| `solace:item` | `item` (id or `#tag`), `consume` (default false) | the player holds `count` of the item (checked once a second). With `consume` the items are taken. Once complete it stays complete. |
| `solace:deliver` | `item`, `npc` (NPC binding id) | the player right-clicks that NPC while carrying `count` of the item; the items are taken |
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
| `solace:has_item` | `item` (id or `#tag`), `count` (default 1), `held` (default false: anywhere in the inventory) |
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

Right-clicking an entity is only intercepted when a binding matches and a dialogue opens; any
other villager trades as usual. The binding id is what `deliver` objectives and `npc` markers use.

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

**Villager gossip factions.** With `source`, standing is not stored by Solace: it is the average
vanilla reputation the villagers within `radius` blocks have of the player — the same gossip that
sets trade prices and that the **Thief** mod builds on, so crimes and gifts count automatically.
A `solace:reputation` effect on such a faction writes real gossip to those villagers. Away from
villagers, the last value seen is used; with no villager nearby a reputation effect does nothing.

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
claim <quest>                            (players) claim rewards
check <quest> <objective>                (players) tick a checkmark objective
```

## In game

- **Quest book**: key `J` by default (Controls → Solace). Tabs: Quests, Factions (only factions you have standing with are listed).
  Optionally also opened by right-clicking an item: set `bookItem` in
  `config/solace-quests-client.json`, e.g. `"minecraft:book"`.
- **HUD tracker**: up to 5 tracked quests. Toggle key is unbound by default. Position and other
  options are in `config/solace-quests-client.json`:
  `hud`, `hudSide` (`left`/`right`), `hudTop` (0–0.9), `toasts`, `compass`, `xaeroWaypoints`,
  `beams`, `bookItem`.
