// Form building blocks. Every control edits the JSON object it was given in place and calls
// `touch` so the caller can mark the entry dirty and redraw what depends on it.
import { CONDITIONS, EFFECTS, Field } from './schema';
import { idsOf, state } from './store';

type Child = Node | string | null | undefined | false;

export function h<K extends keyof HTMLElementTagNameMap>(tag: K, props: Record<string, any> = {}, ...children: Child[]): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  for (const [name, value] of Object.entries(props)) {
    if (name === 'class') {
      element.className = value;
    } else if (name.startsWith('on')) {
      element.addEventListener(name.slice(2), value);
    } else if (value !== false && value !== undefined && value !== null) {
      element.setAttribute(name, value === true ? '' : String(value));
    }
  }
  for (const child of children) {
    if (child !== null && child !== undefined && child !== false) {
      element.append(child);
    }
  }
  return element;
}

function set(target: any, key: string, value: unknown) {
  if (value === undefined || value === '' || (typeof value === 'number' && Number.isNaN(value))) {
    delete target[key];
  } else {
    target[key] = value;
  }
}

/** Text input with a dropdown of matching ids. `options` is read on every keystroke. */
export function suggest(value: string, options: () => string[], onInput: (value: string) => void, placeholder = ''): HTMLElement {
  const list = h('div', { class: 'suggest-list' });
  const input = h('input', { type: 'text', value: value ?? '', placeholder, spellcheck: 'false' });
  const refresh = () => {
    const needle = input.value.toLowerCase();
    list.replaceChildren();
    if (document.activeElement !== input) {
      return;
    }
    let shown = 0;
    for (const option of options()) {
      if (option.toLowerCase().includes(needle) && option !== input.value) {
        list.append(
          h('div', {
            class: 'suggest-item',
            // mousedown, not click: it must win against the input's blur.
            onmousedown: (event: Event) => {
              event.preventDefault();
              input.value = option;
              onInput(option);
              list.replaceChildren();
            },
          }, option),
        );
        if (++shown >= 40) {
          break;
        }
      }
    }
  };
  input.addEventListener('input', () => {
    onInput(input.value);
    refresh();
  });
  input.addEventListener('focus', refresh);
  input.addEventListener('blur', () => list.replaceChildren());
  return h('div', { class: 'suggest' }, input, list);
}

function jsonArea(value: unknown, onValue: (value: unknown) => void): HTMLElement {
  const area = h('textarea', { rows: '4', spellcheck: 'false', class: 'mono' });
  area.value = value === undefined ? '' : JSON.stringify(value, null, 2);
  area.addEventListener('input', () => {
    try {
      onValue(area.value.trim() === '' ? undefined : JSON.parse(area.value));
      area.classList.remove('invalid');
    } catch {
      area.classList.add('invalid'); // keep the last valid value until the JSON parses again
    }
  });
  return area;
}

function control(target: any, field: Field, touch: () => void): HTMLElement {
  const value = target[field.key];
  const update = (next: unknown) => {
    set(target, field.key, next);
    touch();
  };
  switch (field.kind) {
    case 'text':
    case 'long': {
      // A text component: plain words in a normal box, anything richer as JSON.
      if (value !== undefined && typeof value !== 'string') {
        return jsonArea(value, update);
      }
      if (field.kind === 'long') {
        const area = h('textarea', { rows: '3' });
        area.value = value ?? '';
        area.addEventListener('input', () => update(area.value));
        return area;
      }
      return h('input', { type: 'text', value: value ?? '', oninput: (e: Event) => update((e.target as HTMLInputElement).value) });
    }
    case 'string':
      return h('input', { type: 'text', value: value ?? '', spellcheck: 'false', oninput: (e: Event) => update((e.target as HTMLInputElement).value) });
    case 'number':
      return h('input', {
        type: 'number',
        value: value ?? '',
        oninput: (e: Event) => update((e.target as HTMLInputElement).value === '' ? undefined : Number((e.target as HTMLInputElement).value)),
      });
    case 'bool': {
      const box = h('input', { type: 'checkbox' });
      box.checked = value === true;
      box.indeterminate = value === undefined;
      const state = h('span', {}, value === undefined ? 'default' : box.checked ? 'yes' : 'no');
      box.addEventListener('change', () => {
        state.textContent = box.checked ? 'yes' : 'no';
        update(box.checked);
      });
      return h('label', { class: 'check' }, box, state);
    }
    case 'select': {
      const select = h('select', { onchange: (e: Event) => update((e.target as HTMLSelectElement).value) });
      select.append(h('option', { value: '' }, '(default)'));
      for (const option of field.options ?? []) {
        select.append(h('option', { value: option, selected: option === value }, option));
      }
      return select;
    }
    case 'reg':
      return suggest(value, () => state.registries[field.reg!] ?? [], update, state.online ? '' : 'namespace:id');
    case 'ref':
      return suggest(value, () => idsOf(field.ref!), update, 'namespace:id');
    case 'pos': {
      const pos: number[] = Array.isArray(value) ? value : [0, 64, 0];
      const row = h('div', { class: 'row' });
      ['x', 'y', 'z'].forEach((axis, i) => {
        row.append(
          h('input', {
            type: 'number',
            value: pos[i],
            title: axis,
            oninput: (e: Event) => {
              pos[i] = Number((e.target as HTMLInputElement).value) || 0;
              update(pos);
            },
          }),
        );
      });
      return row;
    }
    case 'icon': {
      const icon = value && typeof value === 'object' ? value : {};
      return suggest(icon.item ?? icon.texture ?? '', () => state.registries.items ?? [], (next) => update(next ? { item: next } : undefined), 'minecraft:item');
    }
    case 'condition':
      return conditionEditor(value, update);
    case 'conditions':
      return listEditor(
        Array.isArray(value) ? value : [],
        (item, _i, changed) => conditionEditor(item, (next) => changed(next ?? { type: 'solace:flag', flag: '' })),
        () => ({ type: 'solace:flag', flag: '' }),
        (list) => update(list),
        'Add condition',
      );
    case 'effects':
      return effectsEditor(Array.isArray(value) ? value : [], false, (list) => update(list.length ? list : undefined));
    case 'strings':
      return listEditor(
        Array.isArray(value) ? value : [],
        (item, _i, changed) => h('input', { type: 'text', value: item, spellcheck: 'false', oninput: (e: Event) => changed((e.target as HTMLInputElement).value) }),
        () => '',
        (list) => update(list.length ? list : undefined),
        'Add',
      );
    default:
      return jsonArea(value, update);
  }
}

/** Renders fields as label + control rows; fields marked advanced go into a closed "Advanced" block. */
export function fields(target: any, list: Field[], touch: () => void): HTMLElement {
  const basic = h('div', { class: 'fields' });
  const advanced = h('div', { class: 'fields' });
  for (const field of list) {
    const row = h('div', { class: 'field' },
      h('label', {}, field.label, field.required ? h('span', { class: 'required' }, ' *') : null),
      control(target, field, touch),
      field.help ? h('div', { class: 'help' }, field.help) : null,
    );
    (field.advanced ? advanced : basic).append(row);
  }
  if (advanced.children.length) {
    // Open when it already holds something, so set values are never hidden.
    const used = list.some((field) => field.advanced && target[field.key] !== undefined);
    basic.append(h('details', { open: used }, h('summary', {}, 'Advanced'), advanced));
  }
  return basic;
}

/** A reorderable list. `render` gets a callback that replaces the item. */
export function listEditor<T>(items: T[], render: (item: T, index: number, changed: (next: T) => void) => HTMLElement, make: () => T, commit: (items: T[]) => void, addLabel: string): HTMLElement {
  const box = h('div', { class: 'list' });
  const draw = () => {
    box.replaceChildren();
    items.forEach((item, index) => {
      const tools = h('div', { class: 'item-tools' },
        h('button', { class: 'ghost', title: 'Move up', disabled: index === 0, onclick: () => { items.splice(index - 1, 0, ...items.splice(index, 1)); commit(items); draw(); } }, 'Up'),
        h('button', { class: 'ghost danger', title: 'Remove', onclick: () => { items.splice(index, 1); commit(items); draw(); } }, 'Remove'),
      );
      box.append(h('div', { class: 'item' }, render(item, index, (next) => { items[index] = next; commit(items); }), tools));
    });
    box.append(h('button', { class: 'ghost add', onclick: () => { items.push(make()); commit(items); draw(); } }, '+ ' + addLabel));
  };
  draw();
  return box;
}

/** An object whose "type" (or other discriminator) picks its fields. */
export function typed(target: any, discriminator: string, table: Record<string, Field[]>, touch: () => void, extraHead?: HTMLElement): HTMLElement {
  const body = h('div', {});
  const draw = () => body.replaceChildren(fields(target, table[target[discriminator]] ?? [], touch));
  const select = h('select', {
    onchange: (e: Event) => {
      target[discriminator] = (e.target as HTMLSelectElement).value;
      touch();
      draw();
    },
  });
  for (const type of Object.keys(table)) {
    select.append(h('option', { value: type, selected: type === target[discriminator] }, type.replace('solace:', '')));
  }
  if (!table[target[discriminator]]) {
    select.append(h('option', { value: target[discriminator], selected: true }, String(target[discriminator]) + ' (unknown)'));
  }
  draw();
  return h('div', { class: 'typed' }, h('div', { class: 'row' }, select, extraHead ?? null), body);
}

export function conditionEditor(value: any, update: (value: any) => void): HTMLElement {
  const box = h('div', {});
  const draw = (current: any) => {
    if (!current || typeof current !== 'object') {
      box.className = '';
      box.replaceChildren(h('button', {
        class: 'ghost add',
        onclick: () => {
          const made = { type: 'solace:flag', flag: '' };
          update(made);
          draw(made);
        },
      }, '+ Add condition'));
      return;
    }
    const clear = h('button', { class: 'ghost danger', onclick: () => { update(undefined); draw(undefined); } }, 'Clear');
    box.className = 'nested';
    // Re-send the same object: the parent only needs to know that it changed.
    box.replaceChildren(typed(current, 'type', CONDITIONS, () => update(current), clear));
  };
  draw(value);
  return box;
}

/** Rewards (with ids, claimed individually) and actions share the same effect types. */
export function effectsEditor(items: any[], rewards: boolean, commit: (items: any[]) => void): HTMLElement {
  return listEditor(
    items,
    (item, _index, changed) => {
      const head = rewards
        ? h('input', { type: 'text', class: 'short', placeholder: 'reward id', value: item.id ?? '', spellcheck: 'false', oninput: (e: Event) => { item.id = (e.target as HTMLInputElement).value; changed(item); } })
        : undefined;
      return typed(item, 'type', EFFECTS, () => changed(item), head);
    },
    () => (rewards ? { id: 'reward' + (items.length + 1), type: 'solace:item', item: 'minecraft:bread', count: 1 } : { type: 'solace:flag', flag: '' }),
    commit,
    rewards ? 'Add reward' : 'Add action',
  );
}

export function section(title: string, ...children: Child[]): HTMLElement {
  return h('section', { class: 'section' }, h('h3', {}, title), ...children);
}
