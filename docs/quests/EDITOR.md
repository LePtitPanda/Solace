# Solace Quests — Web editor

One page that edits everything: chapters and their quest graph, quests, dialogue trees, NPC
bindings, factions and markers, with a validation panel.

## Live, with the game running

1. Open a singleplayer or LAN world where you are an operator.
2. Run `/solacequests editor` (or `/sq editor`) and click the link in chat.

The page loads everything the game has loaded, autocompletes ids from the game's registries
(items, blocks, entities, structures, biomes, dimensions, advancements, professions, tags) and
from your own content, and asks the game to validate as you type.

- **Save** writes the entry to `config/solace/quests/<namespace>/<kind>/<path>.json`. A file the
  game would reject is not written; the reason is in **Problems**.
- **Reload game** hot-reloads the content and refreshes the problem list. Nothing changes in the
  running game until you do this.
- **Delete** removes the loose file. If the entry is shipped by a datapack, a small marker file
  hides it instead; the datapack itself is never modified.

The editor never writes outside `config/solace/quests/`. To ship content with the pack, move the
files into the datapack (`data/<namespace>/solace_quests/…`) when you are happy with them.

### Security of the link

- The server listens on `127.0.0.1` only, on a random port, and only after an operator runs the
  command. It stops when the world closes.
- Every start creates a new random token. It is part of the link (after `#`), so it is never sent
  as part of a web address, and the page removes it from the address bar right away. Without the
  token every request is refused. Closing the tab forgets it; run the command again for a new link.
- On a dedicated server the command is refused unless `config/solace-quests.json` contains
  `{"editorAllowDedicated": true}`. Even then it only listens on the server's own `127.0.0.1`, so
  you need an SSH tunnel or similar to reach it. Leave it off unless you know you need it.

## Offline, without the game

Open `Solace/editor/dist/index.html` in a Chromium-based browser (Chrome, Edge) and click
**Open folder**. Pick `config/solace/quests`, a datapack folder, or any folder above them; the
editor finds `<namespace>/<kind>/…` and `data/<namespace>/solace_quests/<kind>/…` layouts and
saves new files in the same layout.

Offline there is no registry autocomplete and no game reload, and validation is limited to
missing required fields and references to entries that do not exist. Run `/sq reload` in the game
afterwards for the full check.

In browsers without folder access (Firefox, Safari) the folder is read-only: **Save** downloads
the file and tells you where it belongs.

## Using it

- **List** (left): everything by kind. A gold dot means unsaved changes; a red number, errors.
- **Canvas** (centre):
  - A chapter or quest shows the chapter's **quest graph**. Drag a quest to move it. Drag the
    round handle on a quest's right edge onto another quest to make the second one require the
    first. Click a line to remove it. Double-click empty space to create a quest there.
  - A dialogue shows its **node graph**. Drag a node's handle onto another node to add a choice
    leading there (or to set "then go to" on a node without choices). Click a line to unlink.
    Double-click empty space for a new node. Node positions are saved in the file as `editor`.
  - Drag the background to pan, use the mouse wheel to zoom.
- **Details** (right): the form for the selection. Less common fields are folded under
  **Advanced**; **Raw JSON** at the bottom edits the file directly.
- **Problems** (top right button): errors and warnings. Click one to jump to its entry.
- **List** and **Details** buttons in the header hide the side panels for more canvas.

## Building the editor

Only needed after changing files in `Solace/editor/src`. Requires Node.js 18 or newer.

```bash
cd Solace/editor
npm install
npm run build
```

This type-checks and produces `editor/dist/index.html`, one self-contained file with no external
requests. It is committed, and `./gradlew build` copies it into the mod at
`assets/solace/quest_editor/index.html`. `npm run dev` serves it with hot reload for working on
the editor itself.

Source layout: `schema.ts` (every field of the content format — forms are generated from it),
`store.ts` (content in memory, the game link, folder access), `forms.ts` (form controls),
`graph.ts` (the SVG graph), `main.ts` (the screens), `style.css`.

When a field is added to the mod, add one line for it in `schema.ts` and in
[AUTHORING.md](AUTHORING.md).
