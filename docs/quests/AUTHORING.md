# Solace Quests — Authoring guide

Covers what is implemented today (phase 2: chapters, quests, phases, objectives, conditions,
rewards). Dialogue, NPCs, factions and markers are added to this guide with their phases; the
planned formats are in [DESIGN.md](DESIGN.md).

Working examples: [samples/astralis](samples/astralis). A deliberately invalid file showing the
error messages: [samples/broken](samples/broken).

## Where files go

| Location | Id of `…/quests/village/first_steps.json` |
|----------|--------------------------------------------|
| Datapack: `data/<namespace>/solace_quests/<kind>/…` | `<namespace>:village/first_steps` |
| Loose: `config/solace/quests/<namespace>/<kind>/…` | `<namespace>:village/first_steps` |

`<kind>` is `chapters` or `quests`. The id always comes from the location; an `id` field is
ignored with a warning.

**Loose files win** over a datapack file with the same id. A loose file containing only
`{"removed": true}` hides the datapack entry.

Apply changes with `/sq reload` (quests only) or `/reload` (all datapacks). Limits: 512 KiB per
file, 32 MiB in total, 20 000 files, 64 levels of JSON nesting.

## Text

Any text field takes vanilla text-component JSON: `"Plain words"`,
`{"translate": "astralis.quest.title"}`, or `{"text": "Gold", "color": "gold"}`.

## Chapter

```json
{ "title": "The Village", "description": "…", "order": 10,
  "unlock": { "type": "solace:flag", "flag": "arrived" } }
```

| Field | Default | Meaning |
|-------|---------|---------|
| `title` | required | Text. |
| `description` | none | Text. |
| `order` | 0 | Sort position in the book. |
| `unlock` | none | Condition; while false every quest in the chapter is locked. |
| `hidden_until_unlocked` | false | Used by the quest book (phase 4). |
| `icon` | none | Used by the quest book (phase 4). |

## Quest

```json
{
  "chapter": "astralis:village",
  "title": "Supplies",
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
| `requires.quests` | none | Quest ids that must have been completed. |
| `requires.mode` | `"all"` | `"all"`, `"any"`, or `{"at_least": N}`. |
| `optional` | false | Not needed for chapter completion (book, phase 4). |
| `hidden_until_unlocked` | false | Book, phase 4. |
| `auto_start` | false | Starts by itself as soon as it is available. |
| `auto_claim` | false | Rewards are handed out on completion instead of on claim. |
| `repeatable` | off | `{"cooldown_seconds": 60}` — can be started again after the cooldown. Progress and claimed rewards reset on restart; unclaimed rewards must be claimed first. |
| `phases` | — | List of phases (below). |
| `objectives`, `transitions`, `story` | — | Shorthand for a single phase called `main`, instead of `phases`. |
| `rewards` | none | List of rewards. |

Accepted but not used yet: `icon`, `position`, `markers`, `time_limit_seconds`, `on_fail`,
`visible_when`, `abandonable`.

### Phase

```json
{ "id": "report", "story": "Time to report back.",
  "objectives": [ { "id": "reported", "type": "solace:checkmark" } ],
  "transitions": [
    { "to": "@fail", "when": { "type": "solace:flag", "flag": "betrayed" }, "label": "betrayal" },
    { "to": "@complete", "when": { "type": "solace:objectives_complete" } }
  ] }
```

- `id` is required and unique in the quest. `story` is shown when the phase begins.
- `transitions` are checked in order; the first whose `when` is true wins. `to` is a phase id,
  `@complete` or `@fail`. `label` is recorded in the player's quest history. A transition without
  `when` always fires.
- **No `transitions`**: when every non-optional objective is done, the quest moves to the next
  phase in the file, or completes after the last one.
- Transitions are re-checked whenever progress, a flag or a variable changes, and once a second
  for time/inventory/dimension conditions.

### Objectives

Common fields: `id` (required, unique in the quest — saved progress is keyed by it), `type`,
`text`, `count` (default 1), `optional` (default false), `when` (condition that must hold for
progress to count).

| Type | Fields | Counts when |
|------|--------|-------------|
| `solace:kill` | `entity` (id or `#tag`), `name` (custom name contains) | the player kills a matching entity |
| `solace:break_block` | `block` (id or `#tag`) | the player breaks a matching block |
| `solace:place_block` | `block` (id or `#tag`) | the player places a matching block |
| `solace:item` | `item` (id or `#tag`), `consume` (default false) | the player holds `count` of the item (checked once a second). With `consume` the items are taken. Once complete it stays complete. |
| `solace:reach_location` | `dimension`, `pos` `[x,y,z]`, `radius` (default 8) | the player is within `radius` blocks (checked once a second) |
| `solace:condition` | `when` (required) | the condition becomes true |
| `solace:checkmark` | — | the player ticks it: `/sq check <quest> <objective>` (a button in the book later) |

### Conditions

| Type | Fields |
|------|--------|
| `solace:all`, `solace:any` | `of`: list of conditions |
| `solace:not` | `condition` |
| `solace:quest_state` | `quest`, `state`: `locked`, `available`, `active`, `claimable`, `completed` (completed at least once), `failed` |
| `solace:phase` | `quest`, `phase`, `reached` (default false: must be the current phase; true: was ever entered) |
| `solace:objectives_complete` | `objectives` (ids; default all non-optional). Only meaningful inside a quest. |
| `solace:flag` | `flag`, `value` (default true) |
| `solace:variable` | `name`, `op` (`==` `!=` `<` `<=` `>` `>=`, default `==`), `value` |
| `solace:has_item` | `item` (id or `#tag`), `count` (default 1), `held` (default false: anywhere in the inventory) |
| `solace:dimension` | `dimension` |
| `solace:time` | `from`, `to` in day ticks 0–23999; `from` > `to` wraps past midnight |
| `solace:predicate` | `predicate`: a vanilla `data/<ns>/predicates` id, tested on the player |

### Rewards

Common fields: `id` (required, unique in the quest), `type`.

| Type | Fields |
|------|--------|
| `solace:item` | `item`, `count` (default 1), `nbt` (SNBT string). Overflow drops at the player's feet. |
| `solace:xp` | `points` or `levels` |
| `solace:loot_table` | `table` |
| `solace:command` | `command`, `permission_level` (0–4, default 2). Runs as the server at the player's position; `@p` is replaced by the player's UUID. |
| `solace:flag` | `flag`, `value` (default true) |
| `solace:variable` | `name`, `op` (`set` or `add`, default `set`), `value` |
| `solace:unlock` | `quest` or `chapter`: makes it available regardless of its requirements |

Players claim with `/sq claim <quest>` until the quest book exists. A reward can never be claimed
twice.

## Commands

`/solacequests` or `/sq`. Everything except `claim` and `check` needs permission level 2.

```
reload                                   hot reload quest content, print the validation report
validate                                 print the report of the last load again
list                                     chapters and their quests
quest start|complete|fail|reset <player> <quest>
quest phase <player> <quest> <phase>
quest progress <player> <quest> <objective> <value>
flag set|clear <player> <flag>
var set|add <player> <name> <value>
debug <player>                           flags, variables, every quest's state and branch path
claim <quest>                            (players) claim rewards
check <quest> <objective>                (players) tick a checkmark objective
```

## Validation messages

Each line is `ERROR|WARN <id> (<kind>) <json path>: <message>`.

A file with an **error** is not applied: its previous valid version stays in use, or the file is
ignored if it never loaded. All other files load normally. **Warnings** never block a file.

| Message | Meaning |
|---------|---------|
| `missing required field` | The field at that path must be present. |
| `expected integer >= N, got …` / `expected a string` / `expected true or false` / `expected an object` / `expected a list` | Wrong JSON type or out of range. |
| `not a valid id: "…"` | Ids are `namespace:path`, lowercase, no spaces. |
| `not a valid text component` | See *Text*. |
| `unknown objective/condition/reward type "…"` | Typo, or a type from a later phase. |
| `duplicate phase/objective/reward id "…"` | Ids must be unique inside one quest. |
| `phase "a" has a transition to unknown phase "b"` | `to` must be a phase id of this quest, `@complete` or `@fail`. |
| `unknown chapters/quests entry "…"` | The file refers to a chapter or quest that is not loaded (missing, or itself rejected). |
| `invalid JSON: …` / `file is larger than 512 KiB` / `JSON is nested deeper than 64 levels` / `total content size limit … exceeded` | The file could not be read at all. |
| WARN `unknown field (ignored)` | Usually a typo in a field name. |
| WARN `unknown item/block/entity_type "…" (is the mod installed?)` | The id is not in the registry; the objective or reward will never match. |
| WARN `kept the previous valid version of this file` / `file ignored until it is fixed` | What happened to a file that had errors. |
