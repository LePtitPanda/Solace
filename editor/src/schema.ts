// What the editor knows about the content format. Forms are generated from these tables, so
// adding a field to the mod means adding one line here. Keep in sync with docs/quests/AUTHORING.md.

export type FieldKind =
  | 'text' // text component: plain string in an input, anything else as JSON
  | 'long' // same, multi-line
  | 'string' // plain string (ids of phases, flags, commands...)
  | 'number'
  | 'bool'
  | 'select'
  | 'reg' // registry id with autocomplete
  | 'ref' // id of another content entry
  | 'pos' // [x, y, z]
  | 'icon' // { item } or { texture }
  | 'condition'
  | 'conditions' // list of conditions
  | 'effects' // list of actions/rewards
  | 'strings' // list of ids
  | 'json'; // raw JSON fallback

export interface Field {
  key: string;
  label: string;
  kind: FieldKind;
  options?: string[];
  reg?: string;
  ref?: string;
  advanced?: boolean;
  required?: boolean;
  help?: string;
}

const f = (key: string, label: string, kind: FieldKind, extra: Partial<Field> = {}): Field => ({ key, label, kind, ...extra });

export const KINDS = ['chapters', 'quests', 'dialogues', 'npcs', 'factions', 'markers'] as const;
export type Kind = (typeof KINDS)[number];
export const KIND_LABEL: Record<Kind, string> = {
  chapters: 'Chapters',
  quests: 'Quests',
  dialogues: 'Dialogues',
  npcs: 'NPCs',
  factions: 'Factions',
  markers: 'Markers',
};

const OPS = ['==', '!=', '<', '<=', '>', '>='];

export const CONDITIONS: Record<string, Field[]> = {
  'solace:all': [f('of', 'All of', 'conditions')],
  'solace:any': [f('of', 'Any of', 'conditions')],
  'solace:not': [f('condition', 'Not', 'condition')],
  'solace:quest_state': [
    f('quest', 'Quest', 'ref', { ref: 'quests', required: true }),
    f('state', 'State', 'select', { options: ['locked', 'available', 'active', 'claimable', 'completed', 'failed'], required: true }),
  ],
  'solace:phase': [
    f('quest', 'Quest', 'ref', { ref: 'quests', required: true }),
    f('phase', 'Phase id', 'string', { required: true }),
    f('reached', 'Ever reached (not only current)', 'bool'),
  ],
  'solace:objectives_complete': [f('objectives', 'Only these objectives (empty = all required)', 'strings')],
  'solace:flag': [f('flag', 'Flag', 'string', { required: true }), f('value', 'Is set', 'bool', { help: 'Default: true' })],
  'solace:variable': [
    f('name', 'Variable', 'string', { required: true }),
    f('op', 'Compare', 'select', { options: OPS }),
    f('value', 'Value', 'number'),
  ],
  'solace:reputation': [
    f('faction', 'Faction', 'ref', { ref: 'factions', required: true }),
    f('op', 'Compare', 'select', { options: OPS }),
    f('value', 'Value', 'number'),
  ],
  'solace:faction_rank': [
    f('faction', 'Faction', 'ref', { ref: 'factions', required: true }),
    f('op', 'Compare', 'select', { options: OPS }),
    f('rank', 'Rank id', 'string', { required: true }),
  ],
  'solace:has_item': [
    f('item', 'Item or #tag', 'reg', { reg: 'items', required: true }),
    f('count', 'Count', 'number'),
    f('held', 'Must be held', 'bool'),
  ],
  'solace:dimension': [f('dimension', 'Dimension', 'reg', { reg: 'dimensions', required: true })],
  'solace:biome': [f('biome', 'Biome or #tag', 'reg', { reg: 'biomes', required: true })],
  'solace:structure': [f('structure', 'Structure or #tag', 'reg', { reg: 'structures', required: true })],
  'solace:time': [f('from', 'From (day tick 0-23999)', 'number'), f('to', 'To', 'number')],
  'solace:advancement': [f('advancement', 'Advancement', 'reg', { reg: 'advancements', required: true })],
  'solace:entity': [
    f('name_contains', 'Name contains', 'string'),
    f('profession', 'Villager profession', 'reg', { reg: 'professions' }),
    f('nbt', 'NBT (SNBT)', 'string'),
  ],
  'solace:predicate': [f('predicate', 'Vanilla predicate id', 'string', { required: true })],
  'solace:custom': [f('handler', 'Handler id', 'string', { required: true }), f('data', 'Data', 'json')],
};

const objectiveCommon: Field[] = [
  f('id', 'Id', 'string', { required: true, help: 'Unique in the quest. Saved progress is keyed by it.' }),
  f('text', 'Text', 'text'),
  f('count', 'Count', 'number'),
  f('optional', 'Optional', 'bool'),
  f('hidden', 'Hidden from the player', 'bool', { advanced: true }),
  f('icon', 'Icon', 'icon', { advanced: true }),
  f('when', 'Only counts when', 'condition', { advanced: true }),
];

export const OBJECTIVES: Record<string, Field[]> = {
  'solace:kill': [f('entity', 'Entity or #tag', 'reg', { reg: 'entities', required: true }), f('name', 'Name contains', 'string')],
  'solace:break_block': [f('block', 'Block or #tag', 'reg', { reg: 'blocks', required: true })],
  'solace:place_block': [f('block', 'Block or #tag', 'reg', { reg: 'blocks', required: true })],
  'solace:interact_block': [f('block', 'Block or #tag', 'reg', { reg: 'blocks', required: true })],
  'solace:interact_entity': [f('entity', 'Entity or #tag', 'reg', { reg: 'entities', required: true }), f('name', 'Name contains', 'string')],
  'solace:item': [f('item', 'Item or #tag', 'reg', { reg: 'items', required: true }), f('consume', 'Take the items', 'bool')],
  'solace:deliver': [f('item', 'Item or #tag', 'reg', { reg: 'items', required: true }), f('npc', 'To NPC', 'ref', { ref: 'npcs', required: true })],
  'solace:craft': [f('item', 'Item or #tag', 'reg', { reg: 'items', required: true })],
  'solace:smelt': [f('item', 'Item or #tag', 'reg', { reg: 'items', required: true })],
  'solace:use_item': [f('item', 'Item or #tag', 'reg', { reg: 'items', required: true })],
  'solace:reach_location': [
    f('dimension', 'Dimension', 'reg', { reg: 'dimensions', required: true }),
    f('pos', 'Position', 'pos', { required: true }),
    f('radius', 'Radius', 'number'),
  ],
  'solace:find_structure': [f('structure', 'Structure or #tag', 'reg', { reg: 'structures', required: true })],
  'solace:enter_dimension': [f('dimension', 'Dimension', 'reg', { reg: 'dimensions', required: true })],
  'solace:enter_biome': [f('biome', 'Biome or #tag', 'reg', { reg: 'biomes', required: true })],
  'solace:dialogue_node': [f('dialogue', 'Dialogue', 'ref', { ref: 'dialogues', required: true }), f('node', 'Node id', 'string', { required: true })],
  'solace:advancement': [f('advancement', 'Advancement', 'reg', { reg: 'advancements', required: true })],
  'solace:reputation': [f('faction', 'Faction', 'ref', { ref: 'factions', required: true }), f('min', 'At least', 'number')],
  'solace:faction_rank': [f('faction', 'Faction', 'ref', { ref: 'factions', required: true }), f('rank', 'Rank id', 'string', { required: true })],
  'solace:condition': [],
  'solace:checkmark': [],
  'solace:custom': [f('handler', 'Handler id', 'string'), f('data', 'Data', 'json')],
};
for (const type of Object.keys(OBJECTIVES)) {
  OBJECTIVES[type] = [objectiveCommon[0], ...OBJECTIVES[type], ...objectiveCommon.slice(1)];
}

const effectCommon: Field[] = [f('text', 'Shown as', 'text', { advanced: true }), f('icon', 'Icon', 'icon', { advanced: true })];

export const EFFECTS: Record<string, Field[]> = {
  'solace:item': [f('item', 'Item', 'reg', { reg: 'items', required: true }), f('count', 'Count', 'number'), f('nbt', 'NBT (SNBT)', 'string', { advanced: true })],
  'solace:deliver': [f('npc', 'Hand in the deliver objectives of NPC', 'ref', { ref: 'npcs', required: true })],
  'solace:take_item': [
    f('item', 'Item', 'reg', { reg: 'items', required: true }),
    f('count', 'Count', 'number'),
    f('optional', 'Continue even if missing', 'bool'),
  ],
  'solace:xp': [f('levels', 'Levels', 'number'), f('points', 'or points', 'number')],
  'solace:loot_table': [f('table', 'Loot table id', 'string', { required: true })],
  'solace:command': [
    f('command', 'Command (@p = the player)', 'string', { required: true }),
    f('permission_level', 'Permission level', 'number', { advanced: true }),
  ],
  'solace:flag': [f('flag', 'Flag', 'string', { required: true }), f('value', 'Set', 'bool', { help: 'Default: true' })],
  'solace:variable': [
    f('name', 'Variable', 'string', { required: true }),
    f('op', 'Operation', 'select', { options: ['set', 'add'] }),
    f('value', 'Value', 'number'),
  ],
  'solace:reputation': [f('faction', 'Faction', 'ref', { ref: 'factions', required: true }), f('amount', 'Amount', 'number')],
  'solace:unlock': [f('quest', 'Quest', 'ref', { ref: 'quests' }), f('chapter', 'or chapter', 'ref', { ref: 'chapters' })],
  'solace:start_quest': [f('quest', 'Quest', 'ref', { ref: 'quests', required: true })],
  'solace:complete_quest': [f('quest', 'Quest', 'ref', { ref: 'quests', required: true })],
  'solace:fail_quest': [f('quest', 'Quest', 'ref', { ref: 'quests', required: true })],
  'solace:set_phase': [f('quest', 'Quest', 'ref', { ref: 'quests', required: true }), f('phase', 'Phase id', 'string', { required: true })],
  'solace:complete_objective': [
    f('quest', 'Quest', 'ref', { ref: 'quests', required: true }),
    f('objective', 'Objective id', 'string', { required: true }),
  ],
  'solace:dialogue': [f('dialogue', 'Dialogue', 'ref', { ref: 'dialogues', required: true })],
  'solace:add_marker': [f('marker', 'Marker', 'ref', { ref: 'markers', required: true })],
  'solace:remove_marker': [f('marker', 'Marker', 'ref', { ref: 'markers', required: true })],
  'solace:close': [],
  'solace:custom': [f('handler', 'Handler id', 'string', { required: true }), f('data', 'Data', 'json')],
  'solace:open_structure_gate': [
    f('structure', 'Structure or #tag', 'reg', { reg: 'structures', required: true }),
    f('mode', 'Mode (empty = config default)', 'select', { options: ['once', 'infinite'] }),
  ],
  'solace:close_structure_gate': [f('structure', 'Structure or #tag', 'reg', { reg: 'structures', required: true })],
  'solace:give_structure_map': [
    f('structure', 'Structure or #tag', 'reg', { reg: 'structures', required: true }),
    f('name', 'Map name', 'text'),
    f('lore', 'Lore (list of text components)', 'json'),
    f('decoration', 'Map decoration (red_x, mansion, monument, target_x...)', 'string'),
    f('search_radius', 'Search radius (chunks, default 100)', 'number', { advanced: true }),
  ],
  'solace:give_structure_compass': [
    f('structure', 'Structure or #tag', 'reg', { reg: 'structures', required: true }),
    f('item', 'Item (default minecraft:compass)', 'reg', { reg: 'items' }),
    f('name', 'Item name', 'text'),
    f('lore', 'Lore (list of text components)', 'json'),
    f('sound', 'Hum sound while held (louder when close)', 'string'),
    f('search_radius', 'Search radius (chunks, default 100)', 'number', { advanced: true }),
  ],
};
for (const type of Object.keys(EFFECTS)) {
  EFFECTS[type] = [...EFFECTS[type], ...effectCommon];
}

export const MARKER_TARGETS: Record<string, Field[]> = {
  position: [f('dimension', 'Dimension', 'reg', { reg: 'dimensions', required: true }), f('pos', 'Position', 'pos', { required: true })],
  structure: [f('structure', 'Structure or #tag', 'reg', { reg: 'structures', required: true })],
  npc: [f('npc', 'NPC', 'ref', { ref: 'npcs', required: true })],
  objective: [f('objective', 'Objective id (reach location)', 'string', { required: true })],
};
const markerCommon: Field[] = [
  f('label', 'Label', 'text'),
  f('color', 'Colour', 'select', { options: ['gold', 'white', 'red', 'green', 'blue', 'aqua', 'yellow', 'purple', 'pink', 'gray'] }),
  f('beam', 'In-world beam', 'bool'),
];
for (const target of Object.keys(MARKER_TARGETS)) {
  MARKER_TARGETS[target] = [...MARKER_TARGETS[target], ...markerCommon];
}

export const CHAPTER: Field[] = [
  f('title', 'Title', 'text', { required: true }),
  f('description', 'Description', 'long'),
  f('icon', 'Icon', 'icon'),
  f('order', 'Order', 'number'),
  f('unlock', 'Unlocked when', 'condition', { advanced: true }),
  f('hidden_until_unlocked', 'Hidden until unlocked', 'bool', { advanced: true }),
  f('hidden_until', 'Hidden from the player until', 'select', {
    options: ['none', 'one_dependency', 'all_dependencies', 'n_dependencies', 'one_objective', 'all_objectives', 'n_objectives'],
    advanced: true,
    help: 'Dependencies = the required quests. Objectives = its own (use with "Starts by itself").',
  }),
  f('hidden_count', 'N (for the n_... choices)', 'number', { advanced: true }),
];

export const QUEST: Field[] = [
  f('title', 'Title', 'text', { required: true }),
  f('description', 'Description', 'long'),
  f('chapter', 'Chapter', 'ref', { ref: 'chapters', required: true }),
  f('icon', 'Icon', 'icon'),
  f('optional', 'Optional', 'bool'),
  f('auto_start', 'Starts by itself', 'bool'),
  f('auto_claim', 'Rewards given automatically', 'bool'),
  f('hidden_until_unlocked', 'Hidden until unlocked', 'bool', { advanced: true }),
  f('time_limit_seconds', 'Time limit (seconds, 0 = none)', 'number', { advanced: true }),
  f('on_fail', 'After failing', 'select', { options: ['keep', 'reset'], advanced: true, help: 'reset = can be tried again' }),
  f('abandonable', 'Player may abandon', 'bool', { advanced: true, help: 'Default: true' }),
  f('visible_when', 'Visible when', 'condition', { advanced: true }),
];

export const PHASE: Field[] = [
  f('id', 'Phase id', 'string', { required: true }),
  f('story', 'Story text', 'long', { help: 'Shown when the phase begins and kept in the journal.' }),
];

export const TRANSITION: Field[] = [
  f('to', 'Go to', 'string', { required: true, help: 'A phase id, @complete or @fail' }),
  f('when', 'When', 'condition'),
  f('label', 'History label', 'string', { advanced: true }),
  f('actions', 'Actions', 'effects', { advanced: true }),
];

export const DIALOGUE_NODE: Field[] = [
  f('speaker', 'Speaker', 'text', { help: 'Empty = the NPC\'s name' }),
  f('text', 'Text', 'long', { required: true, help: 'For variants use a JSON list of {"when": ..., "text": ...}' }),
  f('to', 'Then go to', 'string', { help: 'Only for nodes without choices. A node id or @close' }),
  f('auto_advance_seconds', 'Auto-advance after (seconds)', 'number', { advanced: true }),
  f('cooldown_seconds', 'Cooldown (seconds)', 'number', { advanced: true }),
  f('actions', 'Actions on entering', 'effects', { advanced: true }),
  f('portrait', 'Portrait', 'json', { advanced: true, help: '{"entity": true} or {"item": "minecraft:book"}' }),
];

export const CHOICE: Field[] = [
  f('text', 'Text', 'text', { required: true }),
  f('to', 'Go to', 'string', { help: 'A node id or @close (default)' }),
  f('actions', 'Actions', 'effects'),
  f('visible_when', 'Visible when', 'condition', { advanced: true }),
  f('enabled_when', 'Enabled when', 'condition', { advanced: true }),
  f('disabled_text', 'Text while disabled', 'text', { advanced: true }),
  f('cooldown_seconds', 'Cooldown (seconds)', 'number', { advanced: true }),
];

export const NPC: Field[] = [
  f('entity', 'Entity or #tag', 'reg', { reg: 'entities', required: true }),
  f('when', 'Only when', 'condition', { help: 'Usually solace:entity with a name' }),
  f('dialogue', 'Dialogue', 'ref', { ref: 'dialogues' }),
  f('priority', 'Priority', 'number', { advanced: true }),
  f('freeze', 'Stands still while talking', 'bool', { advanced: true, help: 'Default: true' }),
  f('max_distance', 'Max distance', 'number', { advanced: true }),
  f('dialogues', 'Several dialogues', 'json', { advanced: true, help: '[{"dialogue": id, "when": condition}] - first match wins' }),
];

export const FACTION: Field[] = [
  f('name', 'Name', 'text', { required: true }),
  f('color', 'Colour (#RRGGBB)', 'string'),
  f('icon', 'Icon', 'icon'),
  f('default', 'Starting standing', 'number'),
  f('min', 'Minimum', 'number'),
  f('max', 'Maximum', 'number'),
];
export const RANK: Field[] = [f('id', 'Id', 'string', { required: true }), f('name', 'Name', 'text', { required: true }), f('at', 'From standing', 'number')];
export const RELATION: Field[] = [f('faction', 'Faction', 'ref', { ref: 'factions', required: true }), f('factor', 'Factor (e.g. -0.5)', 'number')];

/** A valid minimal entry for "New ...". */
export function blank(kind: Kind, firstChapter: string): any {
  switch (kind) {
    case 'chapters':
      return { title: 'New chapter', order: 0 };
    case 'quests':
      return { chapter: firstChapter, title: 'New quest', position: { x: 0, y: 0 }, phases: [{ id: 'main', objectives: [] }], rewards: [] };
    case 'dialogues':
      return { start: [{ node: 'start' }], nodes: { start: { text: 'Hello.', choices: [{ text: 'Goodbye.', to: '@close' }] } } };
    case 'npcs':
      return { entity: 'minecraft:villager', when: { type: 'solace:entity', name_contains: 'Name' }, dialogue: '' };
    case 'factions':
      return { name: 'New faction', ranks: [{ id: 'neutral', name: 'Neutral', at: 0 }] };
    case 'markers':
      return { target: 'position', dimension: 'minecraft:overworld', pos: [0, 64, 0], label: 'Marker' };
  }
}
