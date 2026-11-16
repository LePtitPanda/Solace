import './style.css';
import { conditionEditor, effectsEditor, fields, h, listEditor, section, suggest, typed } from './forms';
import { graph, GraphEdge, GraphNode } from './graph';
import {
  blank, CHAPTER, CHOICE, DIALOGUE_NODE, FACTION, Kind, KIND_LABEL, KINDS, MARKER_TARGETS, NPC, OBJECTIVES, PHASE, QUEST,
  RANK, RELATION, TRANSITION,
  LOOT, RECIPE_LOCK, TRADE_LOCK, BLOCK_LOCK,
} from './schema';
import { connect, Entry, entry, idsOf, key, onChange, openFiles, openFolder, reloadGame, remove, save, saveAll, state, validate } from './store';

interface Selection {
  kind: Kind;
  id: string;
  node?: string; // dialogue node
}

let selection: Selection | null = null;
let showNav = true;
let showInspector = true;
let showProblems = false;
const QUEST_DX = 200;
const QUEST_DY = 76;

const app = document.getElementById('app')!;
const els = {
  header: h('header', {}),
  nav: h('nav', {}),
  centre: h('main', {}),
  inspector: h('aside', {}),
  problems: h('footer', {}),
};
app.append(els.header, els.nav, els.centre, els.inspector, els.problems);

/** replaceChildren that ignores the nulls of conditional children. */
const put = (element: HTMLElement, ...children: (Node | string | null | undefined | false)[]) =>
  element.replaceChildren(...(children.filter(Boolean) as (Node | string)[]));
const current = () => (selection ? entry(selection.kind, selection.id) : undefined);
const text = (value: any, fallback = '') => (typeof value === 'string' ? value : value ? JSON.stringify(value) : fallback);
const problemsOf = (e: Entry) => state.problems.filter((p) => p.kind === e.kind && p.id === e.id);

function fail(error: unknown) {
  state.status = 'Error: ' + (error as Error).message;
  light();
}

let lightTimer = 0;
let validateTimer = 0;
/** After an edit: mark dirty and refresh everything except the form being typed in. */
function touch(e: Entry) {
  e.dirty = true;
  clearTimeout(lightTimer);
  lightTimer = window.setTimeout(light, 120);
  clearTimeout(validateTimer);
  validateTimer = window.setTimeout(() => validate(e).then(light, fail), 700);
}

function light() {
  renderHeader();
  renderNav();
  renderCentre();
  renderProblems();
}

function render() {
  app.className = (showNav ? '' : 'no-nav ') + (showInspector && selection ? '' : 'no-inspector ') + (showProblems ? 'problems-open' : '');
  light();
  renderInspector();
}

function select(next: Selection | null) {
  selection = next;
  render();
}

function askId(kind: Kind): string | null {
  const namespace = selection?.id.split(':')[0] ?? [...state.entries.values()][0]?.id.split(':')[0] ?? 'astralis';
  const id = prompt(`Id for the new ${KIND_LABEL[kind].toLowerCase().replace(/s$/, '')} (namespace:path, lowercase):`, namespace + ':');
  if (!id) {
    return null;
  }
  if (!/^[a-z0-9_.-]+:[a-z0-9_.\/-]+$/.test(id) || id.endsWith(':')) {
    alert('Ids look like "astralis:village/first_steps": lowercase letters, digits, _ - . and / only.');
    return null;
  }
  if (entry(kind, id)) {
    alert('That id already exists.');
    return null;
  }
  return id;
}

function create(kind: Kind, patch: (json: any) => void = () => {}) {
  const id = askId(kind);
  if (!id) {
    return;
  }
  const chapter = selection?.kind === 'chapters' ? selection.id : current()?.json.chapter ?? idsOf('chapters')[0] ?? '';
  const json = blank(kind, chapter);
  patch(json);
  state.entries.set(key(kind, id), { kind, id, json, source: 'new', dirty: true });
  select({ kind, id });
}

// ---------------------------------------------------------------- header

function renderHeader() {
  const dirty = [...state.entries.values()].filter((e) => e.dirty).length;
  const errors = state.problems.filter((p) => p.severity === 'error').length;
  put(els.header,
    h('button', { class: 'ghost', title: 'Show or hide the list', onclick: () => { showNav = !showNav; render(); } }, 'List'),
    h('h1', {}, 'Solace Quests'),
    h('span', { class: 'pill ' + (state.online ? 'on' : 'off') }, state.online ? 'Live' : 'Offline'),
    h('span', { class: 'status' }, state.status),
    h('span', { class: 'spacer' }),
    !state.online ? h('button', { onclick: () => openFolder().catch(fail) }, 'Open folder') : null,
    h('button', { disabled: dirty === 0, onclick: () => saveAll().catch(fail) }, dirty ? `Save all (${dirty})` : 'Saved'),
    state.online ? h('button', { class: 'primary', title: 'Hot-reload the content in the running game', onclick: () => reloadGame().catch(fail) }, 'Reload game') : null,
    h('button', { class: 'ghost' + (errors ? ' danger' : ''), onclick: () => { showProblems = !showProblems; render(); } }, `Problems (${state.problems.length})`),
    h('button', { class: 'ghost', title: 'Show or hide the details panel', onclick: () => { showInspector = !showInspector; render(); } }, 'Details'),
  );
}

// ---------------------------------------------------------------- navigator

const openKinds = new Set<string>(['chapters', 'quests', 'dialogues']);

function renderNav() {
  els.nav.replaceChildren();
  for (const kind of KINDS) {
    const items = idsOf(kind);
    const block = h('details', { open: openKinds.has(kind) },
      h('summary', {}, KIND_LABEL[kind], h('span', { class: 'count' }, String(items.length))));
    block.addEventListener('toggle', () => (block.open ? openKinds.add(kind) : openKinds.delete(kind)));
    for (const id of items) {
      const e = entry(kind, id)!;
      const errors = problemsOf(e).filter((p) => p.severity === 'error').length;
      const [namespace, path] = id.split(':');
      block.append(
        h('div', {
          class: 'nav-item' + (selection?.kind === kind && selection.id === id ? ' active' : ''),
          title: id,
          onclick: () => select({ kind, id }),
        },
          h('span', { class: 'nav-title' }, text(e.json.title ?? e.json.name, path)),
          h('span', { class: 'nav-id' }, namespace + ':' + path),
          e.dirty ? h('span', { class: 'dot', title: 'Unsaved changes' }) : null,
          errors ? h('span', { class: 'badge', title: errors + ' error(s)' }, String(errors)) : null,
        ),
      );
    }
    block.append(h('button', { class: 'ghost add', onclick: () => create(kind) }, '+ New ' + KIND_LABEL[kind].toLowerCase().replace(/s$/, '')));
    els.nav.append(block);
  }
}

// ---------------------------------------------------------------- centre

function questGraph(chapterId: string): HTMLElement {
  const quests = [...state.entries.values()].filter((e) => e.kind === 'quests' && e.json.chapter === chapterId);
  const nodes: GraphNode[] = quests.map((e) => ({
    id: e.id,
    x: (e.json.position?.x ?? 0) * QUEST_DX,
    y: (e.json.position?.y ?? 0) * QUEST_DY,
    title: text(e.json.title, e.id),
    subtitle: e.id.split(':')[1],
    accent: e.json.optional ? '#7777AA' : '#D4A843',
  }));
  const edges: GraphEdge[] = [];
  for (const e of quests) {
    for (const required of e.json.requires?.quests ?? []) {
      edges.push({ from: required, to: e.id });
    }
  }
  return graph('quests/' + chapterId, {
    nodes,
    edges,
    selected: selection?.kind === 'quests' ? selection.id : undefined,
    snap: 4,
    hint: 'Drag to move, drag the round handle onto another quest to require it, click a line to remove it, double-click empty space for a new quest.',
    onSelect: (id) => select(id ? { kind: 'quests', id } : { kind: 'chapters', id: chapterId }),
    onMove: (id, x, y) => {
      const e = entry('quests', id)!;
      e.json.position = { x: Math.round(x / QUEST_DX), y: Math.round(y / QUEST_DY) };
      touch(e);
    },
    onLink: (from, to) => {
      const e = entry('quests', to)!;
      e.json.requires ??= { mode: 'all', quests: [] };
      e.json.requires.quests ??= [];
      if (!e.json.requires.quests.includes(from)) {
        e.json.requires.quests.push(from);
        touch(e);
        render();
      }
    },
    onEdge: (edge) => {
      const e = entry('quests', edge.to)!;
      e.json.requires.quests = e.json.requires.quests.filter((id: string) => id !== edge.from);
      if (!e.json.requires.quests.length) {
        delete e.json.requires;
      }
      touch(e);
      render();
    },
    onCreate: (x, y) => create('quests', (json) => {
      json.chapter = chapterId;
      json.position = { x: Math.round(x / QUEST_DX), y: Math.round(y / QUEST_DY) };
    }),
  });
}

/** Nodes without a saved position are laid out in columns by distance from the start nodes. */
function layoutDialogue(json: any): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>();
  const depth = new Map<string, number>();
  const queue: string[] = [];
  const starts = typeof json.start === 'string' ? [json.start] : (json.start ?? []).map((s: any) => s.node);
  for (const start of starts) {
    if (json.nodes?.[start] && !depth.has(start)) {
      depth.set(start, 0);
      queue.push(start);
    }
  }
  while (queue.length) {
    const id = queue.shift()!;
    const node = json.nodes[id];
    for (const next of [node.to, ...(node.choices ?? []).map((c: any) => c.to)]) {
      if (next && json.nodes[next] && !depth.has(next)) {
        depth.set(next, depth.get(id)! + 1);
        queue.push(next);
      }
    }
  }
  const rows = new Map<number, number>();
  for (const id of Object.keys(json.nodes ?? {})) {
    const saved = json.nodes[id].editor;
    const column = depth.get(id) ?? (Math.max(-1, ...depth.values()) + 1);
    const row = rows.get(column) ?? 0;
    rows.set(column, row + 1);
    positions.set(id, saved && typeof saved.x === 'number' ? { x: saved.x, y: saved.y } : { x: column * 230, y: row * 80 });
  }
  return positions;
}

function dialogueGraph(e: Entry): HTMLElement {
  const json = e.json;
  json.nodes ??= {};
  const positions = layoutDialogue(json);
  const starts = new Set<string>(typeof json.start === 'string' ? [json.start] : (json.start ?? []).map((s: any) => s.node));
  const nodes: GraphNode[] = Object.keys(json.nodes).map((id) => ({
    id,
    ...positions.get(id)!,
    title: id + (starts.has(id) ? '  (start)' : ''),
    subtitle: text(json.nodes[id].text, ''),
    accent: starts.has(id) ? '#D4A843' : '#6A4E18',
  }));
  const edges: GraphEdge[] = [];
  for (const [id, node] of Object.entries<any>(json.nodes)) {
    if (node.to && node.to !== '@close' && !(node.choices?.length)) {
      edges.push({ from: id, to: node.to, dashed: true });
    }
    (node.choices ?? []).forEach((choice: any) => {
      if (choice.to && choice.to !== '@close') {
        edges.push({ from: id, to: choice.to, label: text(choice.text) });
      }
    });
  }
  return graph('dialogues/' + e.id, {
    nodes,
    edges,
    selected: selection?.node,
    snap: 10,
    hint: 'Drag the round handle onto another node: adds a choice (or sets "then go to" on a node without choices). Click a line to unlink. Double-click empty space for a new node.',
    onSelect: (id) => select({ kind: 'dialogues', id: e.id, node: id ?? undefined }),
    onMove: (id, x, y) => {
      json.nodes[id].editor = { x, y };
      touch(e);
    },
    onLink: (from, to) => {
      const node = json.nodes[from];
      if (node.choices?.length) {
        node.choices.push({ text: 'New choice', to });
      } else {
        node.to = to;
      }
      touch(e);
      select({ kind: 'dialogues', id: e.id, node: from });
    },
    onEdge: (edge) => {
      const node = json.nodes[edge.from];
      if (node.choices?.length) {
        for (const choice of node.choices) {
          if (choice.to === edge.to && (!edge.label || text(choice.text) === edge.label || text(choice.text).startsWith(edge.label.slice(0, -1)))) {
            choice.to = '@close';
            break;
          }
        }
      } else {
        delete node.to;
      }
      touch(e);
      render();
    },
    onCreate: (x, y) => {
      const id = prompt('Id for the new node (letters, digits, _):');
      if (!id || json.nodes[id]) {
        return;
      }
      json.nodes[id] = { text: '...', to: '@close', editor: { x, y } };
      touch(e);
      select({ kind: 'dialogues', id: e.id, node: id });
    },
  });
}

function renderCentre() {
  const e = current();
  let content: HTMLElement;
  if (!e) {
    content = h('div', { class: 'empty' },
      h('h2', {}, state.entries.size ? 'Pick something on the left' : 'Nothing loaded yet'),
      h('p', {}, state.online
        ? 'Chapters show their quest graph. Dialogues show their node graph.'
        : 'Open a content folder (config/solace/quests or a datapack) with "Open folder", or run /solacequests editor in the game for the live link.'));
  } else if (e.kind === 'chapters') {
    content = questGraph(e.id);
  } else if (e.kind === 'quests') {
    content = questGraph(e.json.chapter);
  } else if (e.kind === 'dialogues') {
    content = dialogueGraph(e);
  } else {
    content = h('div', { class: 'empty' }, h('h2', {}, text(e.json.title ?? e.json.name ?? e.json.label, e.id)), h('p', {}, e.id + ' - edit it in the Details panel.'));
  }
  els.centre.replaceChildren(content);
}

// ---------------------------------------------------------------- inspector

function questForm(e: Entry): HTMLElement[] {
  const json = e.json;
  const t = () => touch(e);
  if (!json.phases) {
    // Single-phase shorthand: edit it as a phase list; the game reads both forms.
    const phase: any = { id: 'main' };
    for (const name of ['story', 'objectives', 'transitions', 'on_enter', 'markers']) {
      if (json[name] !== undefined) {
        phase[name] = json[name];
        delete json[name];
      }
    }
    json.phases = [phase];
  }
  json.rewards ??= [];
  const requires = json.requires ?? {};
  const mode = typeof requires.mode === 'object' ? 'at_least' : requires.mode ?? 'all';
  const setRequires = (quests: string[], nextMode: string, atLeast: number) => {
    if (!quests.length) {
      delete json.requires;
    } else {
      json.requires = { mode: nextMode === 'at_least' ? { at_least: atLeast } : nextMode, quests };
    }
    t();
  };
  const requiredList: string[] = [...(requires.quests ?? [])];
  const requiresBox = h('div', { class: 'fields' },
    h('div', { class: 'field' }, h('label', {}, 'Needs'),
      h('div', { class: 'row' },
        (() => {
          const selectMode = h('select', { onchange: (ev: Event) => { setRequires(requiredList, (ev.target as HTMLSelectElement).value, 1); renderInspector(); } });
          for (const option of ['all', 'any', 'at_least']) {
            selectMode.append(h('option', { value: option, selected: option === mode }, option === 'at_least' ? 'at least N of' : option + ' of'));
          }
          return selectMode;
        })(),
        mode === 'at_least' ? h('input', { type: 'number', class: 'short', value: requires.mode?.at_least ?? 1, oninput: (ev: Event) => setRequires(requiredList, 'at_least', Number((ev.target as HTMLInputElement).value) || 1) }) : null,
      )),
    listEditor(requiredList,
      (item, _i, changed) => suggest(item, () => idsOf('quests').filter((id) => id !== e.id), changed, 'namespace:quest'),
      () => '', (list) => { setRequires(list.filter(Boolean), mode, requires.mode?.at_least ?? 1); light(); }, 'Add required quest'),
  );

  const repeat = json.repeatable ?? {};
  const repeatBox = h('details', { open: json.repeatable !== undefined }, h('summary', {}, 'Repeatable'),
    fields(repeat, [
      { key: 'enabled', label: 'Can be repeated', kind: 'bool' },
      { key: 'cooldown_seconds', label: 'Cooldown (seconds)', kind: 'number' },
    ], () => { json.repeatable = repeat; if (!Object.keys(repeat).length) { delete json.repeatable; } t(); }));

  const phases = listEditor(json.phases as any[],
    (phase) => {
      phase.objectives ??= [];
      const objectives = listEditor(phase.objectives as any[],
        (objective, _i, changed) => typed(objective, 'type', OBJECTIVES, () => changed(objective)),
        () => ({ id: 'objective' + (phase.objectives.length + 1), type: 'solace:kill', entity: 'minecraft:zombie', count: 1 }),
        () => t(), 'Add objective');
      const transitions = listEditor((phase.transitions ??= []) as any[],
        (transition, _i, changed) => fields(transition, TRANSITION, () => changed(transition)),
        () => ({ to: '@complete', when: { type: 'solace:objectives_complete' } }),
        () => { if (!phase.transitions.length) { delete phase.transitions; phase.transitions = []; } t(); }, 'Add transition');
      const markers = listEditor((phase.markers ??= []) as any[],
        (marker, _i, changed) => typeof marker === 'string'
          ? suggest(marker, () => idsOf('markers'), (next) => changed(next), 'namespace:marker')
          : typed(marker, 'target', MARKER_TARGETS, () => changed(marker)),
        () => ({ target: 'objective', objective: '' }), () => t(), 'Add marker');
      return h('details', { class: 'phase', open: json.phases.length < 3 },
        h('summary', {}, 'Phase: ', h('b', {}, phase.id || '(no id)')),
        fields(phase, PHASE, t),
        h('h4', {}, 'Objectives'), objectives,
        h('details', { open: phase.transitions.length > 0 }, h('summary', {}, 'Transitions'),
          h('div', { class: 'help' }, 'None: finishing the objectives moves to the next phase, or completes the quest.'), transitions),
        h('details', { open: (phase.on_enter ?? []).length > 0 }, h('summary', {}, 'Actions when the phase begins'),
          effectsEditor((phase.on_enter ??= []) as any[], false, () => t())),
        h('details', { open: phase.markers.length > 0 }, h('summary', {}, 'Markers'), markers),
      );
    },
    () => ({ id: 'phase' + (json.phases.length + 1), objectives: [] }),
    () => { t(); }, 'Add phase');

  return [
    section('Quest', fields(json, QUEST, t)),
    section('Requirements', requiresBox),
    section('Phases', phases),
    section('Rewards', effectsEditor(json.rewards as any[], true, () => t())),
    section('More', repeatBox),
  ];
}

/** Empty lists the form created for convenience are not worth saving. */
function prune(value: any) {
  if (Array.isArray(value)) {
    value.forEach(prune);
  } else if (value && typeof value === 'object') {
    for (const name of Object.keys(value)) {
      const child = value[name];
      if (Array.isArray(child) && child.length === 0 && ['transitions', 'on_enter', 'markers', 'actions', 'choices'].includes(name)) {
        delete value[name];
      } else {
        prune(child);
      }
    }
  }
}

function dialogueForm(e: Entry): HTMLElement[] {
  const json = e.json;
  const t = () => touch(e);
  const nodeIds = () => ['@close', ...Object.keys(json.nodes ?? {})];
  if (selection?.node && json.nodes?.[selection.node]) {
    const id = selection.node;
    const node = json.nodes[id];
    node.choices ??= [];
    const rename = h('input', { type: 'text', value: id, spellcheck: 'false' });
    rename.addEventListener('change', () => {
      const next = rename.value.trim();
      if (!next || next === id || json.nodes[next]) {
        rename.value = id;
        return;
      }
      // Keep the order of nodes and repoint everything that led to the old id.
      json.nodes = Object.fromEntries(Object.entries(json.nodes).map(([name, value]) => [name === id ? next : name, value]));
      for (const other of Object.values<any>(json.nodes)) {
        if (other.to === id) {
          other.to = next;
        }
        (other.choices ?? []).forEach((choice: any) => { if (choice.to === id) { choice.to = next; } });
      }
      if (typeof json.start === 'string') {
        json.start = json.start === id ? next : json.start;
      } else {
        (json.start ?? []).forEach((start: any) => { if (start.node === id) { start.node = next; } });
      }
      t();
      select({ kind: 'dialogues', id: e.id, node: next });
    });
    const choices = listEditor(node.choices as any[],
      (choice, _i, changed) => fields(choice, CHOICE, () => { changed(choice); }),
      () => ({ text: 'New choice', to: '@close' }), () => { t(); }, 'Add choice');
    return [
      section('Node',
        h('div', { class: 'fields' }, h('div', { class: 'field' }, h('label', {}, 'Node id'), rename)),
        fields(node, DIALOGUE_NODE, t),
        h('div', { class: 'help' }, 'Targets: ' + nodeIds().join(', '))),
      section('Choices', choices),
      section('Node actions',
        h('button', { class: 'ghost', onclick: () => select({ kind: 'dialogues', id: e.id }) }, 'Back to dialogue settings'),
        h('button', {
          class: 'ghost danger',
          onclick: () => {
            if (confirm('Delete node "' + id + '"?')) {
              delete json.nodes[id];
              t();
              select({ kind: 'dialogues', id: e.id });
            }
          },
        }, 'Delete node')),
    ];
  }
  if (typeof json.start === 'string') {
    json.start = [{ node: json.start }];
  }
  json.start ??= [];
  const starts = listEditor(json.start as any[],
    (start, _i, changed) => h('div', { class: 'fields' },
      h('div', { class: 'field' }, h('label', {}, 'Start at node'), suggest(start.node ?? '', () => Object.keys(json.nodes ?? {}), (next) => { start.node = next; changed(start); })),
      h('div', { class: 'field' }, h('label', {}, 'When'), conditionEditor(start.when, (next) => { if (next) { start.when = next; } else { delete start.when; } changed(start); }))),
    () => ({ node: Object.keys(json.nodes ?? {})[0] ?? '' }), () => { t(); }, 'Add start entry');
  return [
    section('Where the conversation starts',
      h('div', { class: 'help' }, 'Checked top to bottom; the first entry whose condition passes wins. Put the fallback (no condition) last.'),
      starts),
    section('Nodes', h('div', { class: 'help' }, 'Click a node in the graph to edit it. Double-click empty space to add one.')),
  ];
}

function factionForm(e: Entry): HTMLElement[] {
  const json = e.json;
  const t = () => touch(e);
  json.ranks ??= [];
  const gossip = h('input', { type: 'checkbox' });
  gossip.checked = json.source?.type === 'solace:villager_gossip';
  gossip.addEventListener('change', () => {
    if (gossip.checked) {
      json.source = { type: 'solace:villager_gossip', radius: 48 };
    } else {
      delete json.source;
    }
    t();
    renderInspector();
  });
  return [
    section('Faction', fields(json, FACTION, t)),
    section('Ranks', h('div', { class: 'help' }, 'A rank applies from its standing upward.'),
      listEditor(json.ranks as any[], (rank, _i, changed) => fields(rank, RANK, () => changed(rank)), () => ({ id: 'rank', name: 'Rank', at: 0 }), () => t(), 'Add rank')),
    section('Relations', h('div', { class: 'help' }, 'Gaining standing here also changes these factions by amount x factor.'),
      listEditor((json.relations ??= []) as any[], (relation, _i, changed) => fields(relation, RELATION, () => changed(relation)), () => ({ faction: '', factor: -0.5 }),
        () => { t(); }, 'Add relation')),
    section('Source',
      h('label', { class: 'check' }, gossip, h('span', {}, 'Mirror vanilla villager gossip (works with the Thief mod)')),
      json.source ? fields(json.source, [{ key: 'radius', label: 'Villagers within (blocks)', kind: 'number' },
        { key: 'defenders_attack_at', label: 'Golems and guards attack at this rank or below (rank id)', kind: 'string' }], t) : null),
  ];
}

function renderInspector() {
  const e = current();
  if (!e) {
    els.inspector.replaceChildren();
    return;
  }
  const t = () => touch(e);
  let body: (HTMLElement | null)[];
  switch (e.kind) {
    case 'chapters':
      body = [section('Chapter', fields(e.json, CHAPTER, t)),
        section('Quests', h('button', { onclick: () => create('quests', (json) => { json.chapter = e.id; }) }, '+ New quest in this chapter'))];
      break;
    case 'quests':
      body = questForm(e);
      break;
    case 'dialogues':
      body = dialogueForm(e);
      break;
    case 'npcs':
      body = [section('NPC binding', fields(e.json, NPC, t))];
      break;
    case 'factions':
      body = factionForm(e);
      break;
    case 'loot':
      body = [section('Quest loot', fields(e.json, LOOT, t))];
      break;
    case 'recipe_locks':
      body = [section('Recipe lock', fields(e.json, RECIPE_LOCK, t))];
      break;
    case 'trades':
      body = [section('Trade lock', fields(e.json, TRADE_LOCK, t))];
      break;
    case 'block_locks':
      body = [section('Block lock', fields(e.json, BLOCK_LOCK, t))];
      break;
    default:
      body = [section('Marker', typed(e.json, 'target', MARKER_TARGETS, t))];
  }
  const raw = h('textarea', { rows: '14', class: 'mono', spellcheck: 'false' });
  const rawBox = h('details', {}, h('summary', {}, 'Raw JSON'), raw,
    h('div', { class: 'help' }, 'Applied when you click outside the box, if it is valid JSON.'));
  rawBox.addEventListener('toggle', () => {
    if (rawBox.open) {
      prune(e.json);
      raw.value = JSON.stringify(e.json, null, 2);
    }
  });
  raw.addEventListener('change', () => {
    try {
      e.json = JSON.parse(raw.value);
      touch(e);
      render();
    } catch {
      raw.classList.add('invalid');
    }
  });
  put(els.inspector,
    h('div', { class: 'inspector-head' },
      h('div', {}, h('div', { class: 'kind' }, KIND_LABEL[e.kind].replace(/s$/, '') + (selection?.node ? ' node' : '')), h('div', { class: 'id' }, e.id + (selection?.node ? ' # ' + selection.node : ''))),
      h('button', { class: 'primary', onclick: () => { prune(e.json); save(e).catch(fail); } }, 'Save'),
      h('button', {
        class: 'ghost danger',
        onclick: () => {
          if (confirm('Delete ' + e.id + '?' + (e.source === 'datapack' ? '\n\nIt comes from a datapack: it will be hidden by a marker file, the pack itself is not changed.' : ''))) {
            selection = null;
            remove(e).catch(fail);
          }
        },
      }, 'Delete')),
    ...body,
    section('Advanced', rawBox),
  );
}

// ---------------------------------------------------------------- problems

function renderProblems() {
  if (!showProblems) {
    els.problems.replaceChildren();
    return;
  }
  const sorted = [...state.problems].sort((a, b) => (a.severity === b.severity ? 0 : a.severity === 'error' ? -1 : 1));
  put(els.problems,
    h('div', { class: 'problems-head' }, h('b', {}, 'Problems'), h('span', { class: 'help' }, 'Errors keep a file from loading; the game then uses its previous valid version. Click one to open it.')),
    sorted.length === 0 ? h('div', { class: 'problem ok' }, 'No problems.') : null,
    ...sorted.map((problem) =>
      h('div', {
        class: 'problem ' + problem.severity,
        onclick: () => {
          const node = /^\$\.nodes\.([^.\[]+)/.exec(problem.path)?.[1];
          if (entry(problem.kind, problem.id)) {
            select({ kind: problem.kind as Kind, id: problem.id, node });
          }
        },
      }, h('span', { class: 'sev' }, problem.severity), h('span', { class: 'where' }, problem.id + ' (' + problem.kind + ')  ' + problem.path), h('span', {}, problem.message))),
  );
}

// ---------------------------------------------------------------- start

const fallback = h('input', { type: 'file', id: 'folder-fallback', webkitdirectory: true, multiple: true, style: 'display:none' });
fallback.addEventListener('change', () => fallback.files && openFiles(fallback.files).catch(fail));
document.body.append(fallback);

window.addEventListener('beforeunload', (event) => {
  if ([...state.entries.values()].some((e) => e.dirty)) {
    event.preventDefault();
  }
});

onChange(render);
render();
connect().catch(fail);
