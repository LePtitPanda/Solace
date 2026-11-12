# Solace Quests — In-game test checklists

## Phase 2 (core)

Setup:

1. Deploy the jar: copy `Solace/build/libs/solace-1.0.jar` to `mods/solace-1.0.jar`.
2. Copy the folder `Solace/docs/quests/samples/astralis` to `config/solace/quests/astralis`.
3. Start a singleplayer world with cheats. The log should contain
   `[Solace Quests] Loaded 1 chapters, 4 quests (0 errors, 0 warnings)`.

Checks (replace `@s` with your name if needed):

| # | Do | Expect |
|---|----|--------|
| 1 | `/sq list` | `astralis:village` and four quests under it. |
| 2 | `/sq quest start @s astralis:village/supplies` then `/sq debug @s` | Starts (admin start ignores requirements). `/sq quest reset @s astralis:village/supplies` removes it again. |
| 3 | `/sq quest start @s astralis:village/first_steps` | Chat: "Quest started: First Steps" and the grey story line. |
| 4 | Kill 3 zombies (`/summon zombie`) | After the third: the "quiet again" story line. `/sq debug @s` shows `phase=report`, `zombies=3`. Killing a skeleton changes nothing. |
| 5 | `/sq check astralis:village/first_steps reported` | "Quest completed: First Steps", "Rewards ready", and "Quest started: Night Watch" (auto-start). |
| 6 | `/sq claim astralis:village/first_steps` twice | First: 3 levels, 6 bread, "Claimed 3 reward(s)". Second: "Nothing to claim". `/sq debug @s` lists the flag `village_trusted`. |
| 7 | `/time set night` | Within a second "Quest completed: Night Watch" and 20 xp (auto-claim). It needed both the flag from step 6 and night. After 60 s it starts again by itself (and completes again while it is still night). |
| 8 | `/sq quest start @s astralis:village/supplies`, hold 8 logs, break 4 stone, place a crafting table | The 8 logs are taken from the inventory; quest completes after all three. `/sq claim astralis:village/supplies` gives toolsmith loot, a gold action-bar line, and `/sq debug @s` shows `gold=10`. |
| 9 | `/sq quest start @s astralis:village/landmark`, then `/tp @s 0 80 0` | Completes within a second and gives 50 xp without claiming. |
| 10 | Quit to title, reopen the world, `/sq debug @s` | All states, flags and `gold=10` are still there. |
| 11 | Copy `Solace/docs/quests/samples/broken/astralis/quests/village/broken.json` into `config/solace/quests/astralis/quests/village/`, then `/sq reload` | Red lines naming `astralis:village/broken` with paths `$.title`, `$.objectives[0].count`, `$.objectives[1].type`; yellow `$.titel`. The other four quests still work. |
| 12 | Edit `first_steps.json` in the config folder: change `"count": 3` to `"count": "x"`, `/sq reload` | Error for that file plus "kept the previous valid version"; the quest still needs 3 zombies. Fix the file and reload: 0 errors. |
| 13 | Delete `landmark.json` from the config folder, `/sq reload`, `/sq debug @s` | The landmark line is shown as `(orphaned)`; put the file back and reload and it is normal again. |
| 14 | `/sq flag set @s betrayed_village`, `/sq quest start @s astralis:village/first_steps`, `/sq quest progress @s astralis:village/first_steps zombies 3` | "Quest failed: First Steps" as soon as the report phase begins (the `@fail` transition is listed first); `/sq debug @s` path ends with `@fail(betrayal)`. |

Afterwards, send `logs/latest.log` if anything differs.
