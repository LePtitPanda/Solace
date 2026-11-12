# Solace Quests — In-game test checklist

None of the client screens, the dialogue flow, the Thief/Xaero/KubeJS bridges or the editor link
have been run in a real game yet. The quest logic, parsing and validation are covered by unit
tests; this list is for everything else. Report the rows that differ, with `logs/latest.log`.

## Setup

1. Copy `Solace/build/libs/solace-1.0.jar` to `mods/solace-1.0.jar`.
2. Replace `config/solace/quests/astralis` with a copy of `Solace/docs/quests/samples/astralis`.
3. Start a **new** singleplayer world with cheats (or run `/sq quest reset @s <quest>` for the
   sample quests in the old one). The log should contain
   `[Solace Quests] Loaded 2 chapters, 6 quests, 1 dialogues, 1 npcs, 3 factions, 1 markers (0 errors, 0 warnings)`.

Replace `@s` with your name where a command wants a player.

## A. Core (commands only)

| # | Do | Expect |
|---|----|--------|
| A1 | `/sq list` | `astralis:village` with four quests, `astralis:wardens` with two. |
| A2 | `/sq validate` | A green summary line, no red lines. |
| A3 | `/sq quest start @s astralis:village/supplies`; hold 8 logs, break 4 stone, place a crafting table | Toasts "Quest started" then "Quest completed". The 8 logs are taken. Crafting 4 torches is optional and not needed. |
| A4 | `/sq claim astralis:village/supplies` twice | First: loot, a gold action-bar line, "Claimed 3 reward(s)". Second: "Nothing to claim". `/sq debug @s` shows `gold=10`. |
| A5 | Quit to title, reopen the world, `/sq debug @s` | Same state as before. |
| A6 | Copy `samples/broken/astralis/quests/village/broken.json` into `config/solace/quests/astralis/quests/village/`, `/sq reload` | Red lines for `astralis:village/broken` at `$.title`, `$.objectives[0].count`, `$.objectives[1].type`; yellow `$.titel`. Everything else still works. Delete the file and reload: 0 errors. |

## B. Dialogue and NPCs

| # | Do | Expect |
|---|----|--------|
| B1 | `/summon villager ~ ~ ~ {CustomName:'"Marrow"'}` and right-click him | The dialogue panel opens at the bottom: name plate "Marrow", text typing out, the villager on the left, three choices on the right once the text is complete. No trading screen. He stops walking and faces you. |
| B2 | Click during typing | The text completes at once. |
| B3 | Pick "What readings?" (click or key `2`) | New text; clicking or Space returns to the first node. |
| B4 | Pick "Can I help with anything?" then "I will deal with them." | Dialogue closes, toast "Quest started — First Steps", grey story line in chat, the villager walks again. |
| B5 | Right-click Marrow again | "Three, I said…" and it closes by itself after 4 seconds. |
| B6 | Kill 3 zombies | Toast "Quest updated". |
| B7 | Right-click Marrow | "Quiet again?" → continue → "Take this candle": 2 candles, then toast "Quest completed", and "Quest started — Night Watch". |
| B8 | Right-click any other villager | Normal trading (or nothing for an unemployed one). |
| B9 | Open the dialogue, press Esc; open it again and walk 10 blocks away | Both close the panel; Marrow moves again each time. |
| B10 | With a second player (LAN), both talk to Marrow at once | Each has their own conversation. |
| B11 | Talk to Marrow after completing First Steps, without a diamond | "I brought you a diamond. (you have none)" is greyed out and cannot be picked. With a diamond in the inventory it can: the diamond is taken. |

## C. Quest book, journal, HUD

| # | Do | Expect |
|---|----|--------|
| C1 | Press `J` | The quest book in Solace colours. Left: "The Village" and "The Wardens" (hidden until First Steps was completed in B7), each with a done/total count. Middle: quest nodes with icons and lines from First Steps to the quests that need it. |
| C2 | Drag the background, scroll the wheel | The graph pans and zooms. |
| C3 | Click a node | A details panel on the right: title, status, description, objectives with counts, rewards, story so far. |
| C4 | Select First Steps (rewards ready), click "Claim rewards" | 3 levels, 6 bread; rewards show `[x]`; the node dims. |
| C5 | Start `astralis:village/landmark` by command, open the book, select it | "In progress" with a countdown, buttons "Untrack" and "Abandon". Holding "Abandon" until it fills removes the quest. |
| C7 | Close the book with an active tracked quest | A tracker on the right edge: quest title, objectives with `n/N`. Hidden with F1. |
| C8 | `/time set night` | Toast "Quest completed — Night Watch" (needs the flag from C4). |
| C9 | In Controls → Solace, bind "Toggle Quest Tracker" and press it | The tracker hides/shows. |

## D. Factions and reputation

| # | Do | Expect |
|---|----|--------|
| D1 | Book → Factions | Wardens (blue), Outlaws (red), Villagers (green) with rank names and bars. After C4: Wardens 20, Outlaws −10. |
| D2 | `/sq rep add @s astralis:wardens 40` | Wardens 60 "Friendly" with a toast; Outlaws drops by 20 to −30 (the −0.5 relation). `/sq rep get @s astralis:outlaws` confirms. |
| D3 | Talk to Marrow, pick "Give me something to do. (3 bread)"; then right-click Marrow carrying 3 bread, ring a bell, and click "Swear to keep the roads safe" in the book | Quest "A Warden's Errand": the bread is taken on the right-click, the checkmark objective is clickable in the details panel, the quest completes. "Trusted" starts by itself and completes at once (already Friendly). |
| D4 | Stand among villagers, `/sq rep add @s astralis:villagers 30`, wait a few seconds, open Factions | Villagers standing rises and trade prices drop. `/sq rep set` on this faction is refused with an explanation. |
| D5 | (Thief) Steal from a villager's chest while watched | Villagers standing drops within a moment; a toast if the rank changed. |

## E. Markers

| # | Do | Expect |
|---|----|--------|
| E1 | Start `astralis:village/landmark`, track it | The HUD tracker shows an arrow and distance for it; the arrow points toward 0,0 and turns as you turn. |
| E2 | Look toward 0,0 from within 256 blocks | A thin gold column at the marker. |
| E3 | (Xaero) Open the minimap and world map | A temporary waypoint "World origin". It is not in your waypoint list after relogging without the quest, and your own waypoints are untouched. |
| E4 | `/tp @s 0 80 0` | Quest completes, 50 XP, the marker and waypoint disappear. |
| E5 | During First Steps' report phase (B6), stand near Marrow | A marker/waypoint "Marrow" at his position, following him every couple of seconds. |

## F. Web editor

| # | Do | Expect |
|---|----|--------|
| F1 | `/sq editor`, click the link | The browser opens the editor, "Live", with the sample content listed. The address bar shows no token. |
| F2 | Click "The Village" | The quest graph. Drag a quest: it snaps to a grid cell and gets a gold dot in the list. |
| F3 | Change First Steps' title, click Save, then "Reload game"; press `J` in the game | The new title. The file is in `config/solace/quests/astralis/quests/village/`. |
| F4 | Set an objective's count to empty and its type to something invalid through Raw JSON, Save | "Not saved … has errors"; the Problems panel lists it; the file on disk is unchanged. |
| F5 | Open the dialogue, drag a node's handle onto another node, Save, Reload game, talk to Marrow | The new choice is there. |
| F6 | Copy the link, close the world, reopen it, use the old link | "missing or wrong token" / cannot reach the game. `/sq editor` gives a working link again. |
| F7 | Open `Solace/editor/dist/index.html` from disk in Chrome or Edge, "Open folder", pick `config/solace/quests` | "Offline", the same content, saving writes the files. |

## G. KubeJS (optional)

Add to `kubejs/server_scripts/solace_quests_test.js`, then `/reload`:

```js
SolaceQuestEvents.questStarted(event => event.player.tell('KubeJS saw ' + event.id))
```

Starting any quest should print the line in chat. If KubeJS reports an error mentioning
`SolaceQuestEvents` at startup, send the log.
