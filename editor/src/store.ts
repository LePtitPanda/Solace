// Content in memory, and the two ways it gets in and out: the live link to a running game
// (token in the URL fragment) or a folder opened from disk when the game is not running.
import { KINDS, Kind } from './schema';

export interface Entry {
  kind: Kind;
  id: string; // namespace:path
  json: any;
  source: string; // datapack | loose | folder | new
  dirty: boolean;
  handle?: any; // FileSystemFileHandle when opened from a folder
}

export interface Problem {
  kind: string;
  id: string;
  path: string;
  severity: 'error' | 'warning';
  message: string;
}

export const state = {
  entries: new Map<string, Entry>(),
  problems: [] as Problem[],
  registries: {} as Record<string, string[]>,
  online: false,
  folder: null as any, // FileSystemDirectoryHandle
  layout: 'loose' as 'loose' | 'datapack',
  prefix: [] as string[], // folders between the opened folder and the content root
  status: 'Not connected. Open a content folder, or run /solacequests editor in the game.',
};

let token = '';
let changed: () => void = () => {};
export const onChange = (listener: () => void) => (changed = listener);
export const notify = () => changed();

export const key = (kind: string, id: string) => kind + '/' + id;
export const entry = (kind: string, id: string) => state.entries.get(key(kind, id));
export const idsOf = (kind: string) =>
  [...state.entries.values()].filter((e) => e.kind === kind).map((e) => e.id).sort();

async function api(method: string, path: string, body?: unknown): Promise<any> {
  const response = await fetch('/api/' + path, {
    method,
    headers: { Authorization: 'Bearer ' + token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok && response.status !== 422) {
    throw new Error(json.error || response.status + ' ' + response.statusText);
  }
  return json;
}

function setContent(content: any) {
  const unsaved = [...state.entries.values()].filter((e) => e.dirty);
  state.entries.clear();
  for (const item of content.entries as any[]) {
    state.entries.set(key(item.kind, item.id), { kind: item.kind, id: item.id, json: item.json, source: item.source, dirty: false });
  }
  // Never lose edits the user has not saved yet because the game reloaded.
  for (const item of unsaved) {
    state.entries.set(key(item.kind, item.id), item);
  }
  state.problems = content.problems;
}

/** Called once at startup. Online when the page was opened through the game's link. */
export async function connect() {
  const fragment = new URLSearchParams(location.hash.slice(1));
  token = fragment.get('token') || sessionStorage.getItem('solace-token') || '';
  if (fragment.has('token')) {
    // Keep the token out of the address bar and the history; this tab remembers it until closed.
    sessionStorage.setItem('solace-token', token);
    history.replaceState(null, '', location.pathname);
  }
  if (!token || !location.protocol.startsWith('http')) {
    return;
  }
  try {
    await api('GET', 'session');
    state.registries = await api('GET', 'registries');
    setContent(await api('GET', 'content'));
    state.online = true;
    state.status = 'Connected to the game.';
  } catch (error) {
    state.status = 'Could not reach the game (' + (error as Error).message + '). Run /solacequests editor again.';
  }
  notify();
}

function pathOf(e: Entry): string[] {
  const [namespace, path] = e.id.split(':');
  const tail = [e.kind, ...path.split('/')];
  tail[tail.length - 1] += '.json';
  return [...state.prefix, ...(state.layout === 'datapack' ? ['data', namespace, 'solace_quests', ...tail] : [namespace, ...tail])];
}

/** Saves one entry. Online the game validates first and refuses a file it would reject. */
export async function save(e: Entry) {
  if (state.online) {
    const [namespace, path] = e.id.split(':');
    const result = await api('PUT', `content/${e.kind}/${namespace}/${path}`, e.json);
    state.problems = state.problems.filter((p) => !(p.kind === e.kind && p.id === e.id)).concat(result.problems);
    if (result.saved) {
      e.dirty = false;
      e.source = 'loose';
      state.status = 'Saved ' + e.id + '. Use "Reload game" to apply.';
    } else {
      state.status = 'Not saved: ' + e.id + ' has errors (see Problems).';
    }
  } else if (state.folder) {
    if (!e.handle) {
      let directory = state.folder;
      const segments = pathOf(e);
      for (const segment of segments.slice(0, -1)) {
        directory = await directory.getDirectoryHandle(segment, { create: true });
      }
      e.handle = await directory.getFileHandle(segments[segments.length - 1], { create: true });
    }
    const writable = await e.handle.createWritable();
    await writable.write(JSON.stringify(e.json, null, 2) + '\n');
    await writable.close();
    e.dirty = false;
    state.status = 'Saved ' + e.id + '.';
  } else {
    // No folder access in this browser: hand the file to the user.
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([JSON.stringify(e.json, null, 2) + '\n'], { type: 'application/json' }));
    link.download = e.id.split(':')[1].split('/').pop() + '.json';
    link.click();
    URL.revokeObjectURL(link.href);
    e.dirty = false;
    state.status = 'Downloaded ' + link.download + ' - put it at ' + pathOf(e).join('/');
  }
  notify();
}

export async function saveAll() {
  for (const e of [...state.entries.values()].filter((item) => item.dirty)) {
    await save(e);
  }
}

export async function remove(e: Entry) {
  if (state.online && e.source !== 'new') {
    const [namespace, path] = e.id.split(':');
    await api('DELETE', `content/${e.kind}/${namespace}/${path}`);
  } else if (e.handle && state.folder) {
    let directory = state.folder;
    const segments = pathOf(e);
    for (const segment of segments.slice(0, -1)) {
      directory = await directory.getDirectoryHandle(segment);
    }
    await directory.removeEntry(segments[segments.length - 1]);
  }
  state.entries.delete(key(e.kind, e.id));
  state.status = 'Deleted ' + e.id + '.';
  notify();
}

export async function reloadGame() {
  setContent(await api('POST', 'reload'));
  const errors = state.problems.filter((p) => p.severity === 'error').length;
  state.status = 'Game reloaded: ' + errors + ' error(s), ' + (state.problems.length - errors) + ' warning(s).';
  notify();
}

/** Asks the game to validate without saving. Offline falls back to the local checks. */
export async function validate(e: Entry) {
  if (!state.online) {
    validateLocal();
    return;
  }
  const [namespace, path] = e.id.split(':');
  const result = await api('POST', `content/${e.kind}/${namespace}/${path}`, e.json);
  state.problems = state.problems.filter((p) => !(p.kind === e.kind && p.id === e.id)).concat(result.problems);
  notify();
}

function place(segments: string[]): { kind: Kind; id: string; layout: 'loose' | 'datapack'; prefix: string[] } | null {
  const last = segments[segments.length - 1];
  if (!last.endsWith('.json')) {
    return null;
  }
  const parts = [...segments.slice(0, -1), last.slice(0, -5)];
  const marker = parts.indexOf('solace_quests');
  // Datapack: .../data/<namespace>/solace_quests/<kind>/<path>. Loose: <namespace>/<kind>/<path>.
  const at = marker >= 1 ? marker - 1 : parts.findIndex((_, i) => (KINDS as readonly string[]).includes(parts[i + 1]));
  const kindAt = marker >= 1 ? marker + 1 : at + 1;
  if (at < 0 || !(KINDS as readonly string[]).includes(parts[kindAt]) || parts.length <= kindAt + 1) {
    return null;
  }
  return {
    kind: parts[kindAt] as Kind,
    id: parts[at] + ':' + parts.slice(kindAt + 1).join('/'),
    layout: marker >= 1 ? 'datapack' : 'loose',
    prefix: marker >= 1 ? parts.slice(0, Math.max(0, marker - 2)) : parts.slice(0, at),
  };
}

async function walk(directory: any, segments: string[], visit: (segments: string[], handle: any) => Promise<void>) {
  for await (const [name, handle] of directory.entries()) {
    if (handle.kind === 'directory') {
      await walk(handle, [...segments, name], visit);
    } else {
      await visit([...segments, name], handle);
    }
  }
}

function loaded(count: number) {
  state.online = false;
  state.status = 'Loaded ' + count + ' file(s) from disk (offline: no registry autocomplete, no game reload).';
  validateLocal();
  notify();
}

/** Offline: open config/solace/quests, a datapack, or any folder above them, for reading and writing. */
export async function openFolder() {
  const picker = (window as any).showDirectoryPicker;
  if (!picker) {
    document.getElementById('folder-fallback')!.click();
    return;
  }
  const folder = await picker({ mode: 'readwrite' });
  state.entries.clear();
  state.problems = [];
  state.prefix = [];
  state.folder = folder;
  let count = 0;
  await walk(folder, [], async (segments, handle) => {
    const found = place(segments);
    if (!found) {
      return;
    }
    try {
      const json = JSON.parse(await (await handle.getFile()).text());
      state.layout = found.layout;
      state.prefix = found.prefix;
      state.entries.set(key(found.kind, found.id), { kind: found.kind, id: found.id, json, source: 'folder', dirty: false, handle });
      count++;
    } catch {
      state.problems.push({ kind: found.kind, id: found.id, path: '$', severity: 'error', message: 'invalid JSON' });
    }
  });
  loaded(count);
}

/** Fallback for browsers without folder access: read-only load; saving downloads the file. */
export async function openFiles(files: FileList) {
  state.entries.clear();
  state.folder = null;
  let count = 0;
  for (const file of Array.from(files)) {
    const found = place((file as any).webkitRelativePath.split('/').slice(1));
    if (!found) {
      continue;
    }
    try {
      state.layout = found.layout;
      state.prefix = found.prefix;
      state.entries.set(key(found.kind, found.id), { kind: found.kind, id: found.id, json: JSON.parse(await file.text()), source: 'folder', dirty: false });
      count++;
    } catch {
      state.problems.push({ kind: found.kind, id: found.id, path: '$', severity: 'error', message: 'invalid JSON' });
    }
  }
  loaded(count);
}

function refs(value: any, path: string, out: { kind: string; id: string; path: string }[]) {
  const REF_KEYS: Record<string, string> = { quest: 'quests', chapter: 'chapters', dialogue: 'dialogues', npc: 'npcs', faction: 'factions', marker: 'markers' };
  if (Array.isArray(value)) {
    value.forEach((item, i) => refs(item, path + '[' + i + ']', out));
  } else if (value && typeof value === 'object') {
    for (const [name, child] of Object.entries(value)) {
      if (typeof child === 'string' && REF_KEYS[name] && child.includes(':')) {
        out.push({ kind: REF_KEYS[name], id: child, path: path + '.' + name });
      } else {
        refs(child, path + '.' + name, out);
      }
    }
  }
}

/**
 * The checks that need no game: required top-level fields and references to entries that do
 * not exist. The game's own validation (online) is the authority; this only catches the obvious.
 */
export function validateLocal() {
  const required: Record<string, string[]> = { chapters: ['title'], quests: ['chapter', 'title'], dialogues: ['start', 'nodes'], npcs: ['entity'], factions: ['name', 'ranks'], markers: [] };
  const problems: Problem[] = [];
  for (const e of state.entries.values()) {
    for (const field of required[e.kind]) {
      if (e.json[field] === undefined || e.json[field] === '') {
        problems.push({ kind: e.kind, id: e.id, path: '$.' + field, severity: 'error', message: 'missing required field' });
      }
    }
    const found: { kind: string; id: string; path: string }[] = [];
    refs(e.json, '$', found);
    for (const ref of found) {
      if (!state.entries.has(key(ref.kind, ref.id))) {
        problems.push({ kind: e.kind, id: e.id, path: ref.path, severity: 'error', message: `unknown ${ref.kind} entry "${ref.id}"` });
      }
    }
    for (const required of e.kind === 'quests' ? e.json.requires?.quests ?? [] : []) {
      if (!state.entries.has(key('quests', required))) {
        problems.push({ kind: e.kind, id: e.id, path: '$.requires.quests', severity: 'error', message: `unknown quests entry "${required}"` });
      }
    }
  }
  state.problems = problems;
}
