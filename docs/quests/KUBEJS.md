# Solace Quests — KubeJS and Java API

Everything here runs on the server. KubeJS is optional; the same things are available to Java
mods through `com.solace.quests.api.QuestApi` and the Forge event `com.solace.quests.api.QuestEvent`.

## Events (`server_scripts`)

```js
SolaceQuestEvents.questCompleted(event => {
  event.player.tell(`Finished ${event.id}`)
})

SolaceQuestEvents.objectiveProgressed(event => {
  // event.id = quest, event.detail = objective id, event.value = new progress
})
```

| Event | `id` | `detail` | `value` |
|-------|------|----------|---------|
| `questStarted` | quest | — | 0 |
| `questCompleted` | quest | — | 0 |
| `questFailed` | quest | — | 0 |
| `phaseChanged` | quest | new phase id | 0 |
| `objectiveProgressed` | quest | objective id | new progress |
| `dialogueChoice` | dialogue | node id | index of the choice in the file |
| `reputationChanged` | faction | new rank id | new standing |

Every event has `player` (and KubeJS's usual `entity`, `level`, `server`). `id` is a string such
as `"astralis:village/first_steps"`.

## The `SolaceQuests` binding

```js
SolaceQuests.progress(player, 'astralis:village/first_steps', 'my_objective', 1)
SolaceQuests.start(player, 'astralis:village/first_steps')      // only if available
SolaceQuests.state(player, 'astralis:village/first_steps')      // locked | available | active | claimable | completed | failed | unknown
SolaceQuests.hasFlag(player, 'village_trusted')
SolaceQuests.setFlag(player, 'village_trusted', true)
SolaceQuests.getVariable(player, 'gold')
SolaceQuests.setVariable(player, 'gold', 25)
SolaceQuests.getReputation(player, 'astralis:wardens')
SolaceQuests.addReputation(player, 'astralis:wardens', 10)

// Structure gates (see ../structure-gates.md). Same code as /solace gates and the quest effects.
SolaceQuests.openStructureGate(server, 'irons_spellbooks:evoker_fort', 'once')  // mode null = config default
SolaceQuests.closeStructureGate(server, '#astralis:host_outposts')
SolaceQuests.structureGateStatus(server, 'irons_spellbooks:evoker_fort')       // "open (once)", "closed | generated at ..."
```

`player` is a server player (`event.player` in server events).

## Custom objectives

Give the objective the type `solace:custom` and advance it from a script:

```json
{ "id": "ore", "type": "solace:custom", "count": 5, "text": "Mine diamond ore by hand" }
```

```js
BlockEvents.broken('minecraft:diamond_ore', event => {
  if (event.player && event.player.mainHandItem.empty) {
    SolaceQuests.progress(event.player, 'astralis:village/prospector', 'ore', 1)
  }
})
```

`progress` does nothing (and returns false) unless that quest is active and the objective is in
its current phase, so it is safe to call unconditionally.

## Custom conditions

```js
SolaceQuests.registerCondition('is_sneaking', (player, data) => player.isCrouching())
SolaceQuests.registerCondition('level_at_least', (player, data) => player.xpLevel >= JSON.parse(data).level)
```

```json
{ "type": "solace:custom", "handler": "level_at_least", "data": { "level": 10 } }
```

`data` arrives as JSON text (`"null"` when the field is absent). Conditions are evaluated often —
at least once a second per player with active quests — so keep them cheap and free of side
effects. An unregistered handler counts as false.

## Custom rewards and actions

```js
SolaceQuests.registerEffect('lightning', (player, data) => {
  player.server.runCommandSilent(`summon lightning_bolt ${player.x} ${player.y} ${player.z}`)
})
```

```json
{ "id": "boom", "type": "solace:custom", "handler": "lightning", "text": "A sign from above" }
```

The script lines above use ordinary KubeJS 6 calls and are examples, not tested recipes.

Register handlers at the top level of a server script (not inside an event), so they exist again
after `/reload`.

## Java

```java
QuestApi.registerCondition("mymod:in_guild", (player, json) -> Guilds.isMember(player));
MinecraftForge.EVENT_BUS.addListener((QuestEvent event) -> {
    if (event.type == QuestEvent.Type.QUEST_COMPLETED) { /* ... */ }
});
```
