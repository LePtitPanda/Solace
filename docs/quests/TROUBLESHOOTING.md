# Solace Quests — Troubleshooting

## Reading the validation report

Shown by `/sq reload`, `/sq validate`, in `logs/latest.log` (search for `[Solace Quests]`) and in
the editor's **Problems** panel. Each line is

```
ERROR|WARN <id> (<kind>) <json path>: <message>
```

for example `ERROR astralis:village/first_steps (quests) $.phases[1].objectives[0].count: expected integer >= 1, got "three"`.

- A file with an **error** is not applied. Its previous valid version stays in use until the game
  restarts; if it never loaded, the file is ignored. Every other file loads normally.
- **Warnings** never block a file.
- Player progress is never deleted by a reload. Progress for a quest that disappeared is kept and
  shown as `(orphaned)` by `/sq debug`; it comes back when the quest does.

| Message | Meaning and fix |
|---------|-----------------|
| `missing required field` | The field at that path must be present. |
| `expected integer >= N, got …`, `expected a string`, `expected true or false`, `expected an object`, `expected a list` | Wrong JSON type or out of range. |
| `not a valid id: "…"` | Ids are `namespace:path`: lowercase letters, digits, `_ - . /`. |
| `not a valid text component` | See *Text* in [AUTHORING.md](AUTHORING.md). |
| `unknown objective / condition / reward / action type "…"` | Typo in `type`. |
| `duplicate phase / objective / reward id "…"` | Ids must be unique inside one quest. |
| `phase "a" has a transition to unknown phase "b"` | `to` must be a phase id of this quest, `@complete` or `@fail`. |
| `unknown node "…"` | A dialogue `start`, `to` or choice `to` names a node that is not in `nodes`. |
| `a node has either "choices" or "to", not both` | Remove one of them. |
| `a condition objective needs "when"` | `solace:condition` objectives complete when their `when` is true. |
| `an NPC binding needs "dialogue" or "dialogues"` | Add one. |
| `a faction needs at least one rank` | Add a `ranks` entry. |
| `an "objective" marker only works inline in a quest or phase` | Move it out of `markers/` into the quest. |
| `not valid SNBT: …` | The `nbt` string does not parse; test it with `/give`. |
| `unknown chapters / quests / dialogues / npcs / factions / markers entry "…"` | The file refers to an entry that is not loaded: missing, misspelt, or itself rejected (fix that one first). |
| `invalid JSON: …` | Syntax error; the message has the line and column. |
| `file is larger than 512 KiB`, `JSON is nested deeper than 64 levels`, `total content size limit … exceeded`, `too many content files` | Size limits. Split the file. |
| WARN `unknown field (ignored)` | Usually a typo in a field name. |
| WARN `unknown item / block / entity_type "…" (is the mod installed?)` | The id is not registered; that objective or reward will never match. |
| WARN `ignored: the id comes from the file location` | Remove the `id` field. |
| WARN `kept the previous valid version of this file` / `file ignored until it is fixed` | What happened to a file that had errors. |

## Things that do not work as expected

| Symptom | Likely cause |
|---------|--------------|
| My file is not listed at all | Wrong folder. Loose: `config/solace/quests/<namespace>/<kind>/x.json`. Datapack: `data/<namespace>/solace_quests/<kind>/x.json`. `<kind>` must be one of the six names. |
| Datapack edits do nothing | A loose file with the same id in `config/solace/quests` overrides it. |
| Right-clicking the NPC opens trading | No binding matched: check `entity`, and that `name_contains` matches the custom name exactly (case-sensitive). `/sq dialogue open <player> <dialogue>` tests the dialogue alone. |
| The dialogue opens then closes at once | The start node is on `cooldown_seconds`, or the player is further than `max_distance`. |
| A choice is missing | Its `visible_when` is false, or it is on cooldown. Greyed out means `enabled_when` is false. |
| "You do not have what this requires." | The choice has a `solace:take_item` action and the player lacks the items. Add `"optional": true` to continue anyway. |
| A quest does not start from a dialogue | `solace:start_quest` never restarts an active or finished quest. See `/sq debug <player>`. |
| An objective does not progress | It is not in the quest's **current** phase, its `when` is false, or the id/tag does not match. `/sq debug <player>` shows the phase and progress. |
| `solace:find_structure` never completes | The player must stand inside a piece of the structure, not merely in its chunk. |
| A `structure` marker never appears | None was found within 64 chunks when the marker first showed. The result is remembered per player. |
| Villager faction standing does not change | It only updates near villagers (within the faction's `radius`), every few seconds. A reputation effect with no villager nearby does nothing. |
| No waypoints on Xaero's map | `xaeroWaypoints` is false in `config/solace-quests-client.json`, or the log has "Xaero's Minimap waypoints disabled" (incompatible Xaero version). The HUD arrow still works. |
| The book key does nothing | `J` may be bound to something else: Controls → Solace → Open Quest Book. |
| The log says "Effect chain deeper than 8" | Actions trigger each other in a loop (for example a phase's `on_enter` that sets its own phase). |
| The log says "solace_quests.dat has schema N but this build only knows M" | The world was last played with a newer Solace. Progress is neither loaded nor overwritten; update Solace. |
| "The editor page is not in this build of Solace" | The jar was built without `editor/dist/index.html`. See *Building the editor* in [EDITOR.md](EDITOR.md). |
| The editor says "missing or wrong token" | The world was reopened or the link is old. Run `/sq editor` again. |

## Where things are stored

| What | Where |
|------|-------|
| Player progress | `<world>/data/solace_quests.dat` |
| Loose content | `config/solace/quests/` |
| Client options | `config/solace-quests-client.json` |
| Server options | `config/solace-quests.json` (optional; only `editorAllowDedicated`) |
