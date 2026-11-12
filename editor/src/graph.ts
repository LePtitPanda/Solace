// A small SVG node graph: pan (drag the background), zoom (wheel), move nodes (drag),
// link nodes (drag from a node's handle onto another node). Used for quests and dialogue nodes.

export interface GraphNode {
  id: string;
  x: number;
  y: number;
  title: string;
  subtitle?: string;
  accent?: string;
}

export interface GraphEdge {
  from: string;
  to: string;
  label?: string;
  dashed?: boolean;
}

export interface GraphOptions {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selected?: string;
  /** Snap node positions to this many pixels. */
  snap: number;
  hint: string;
  onSelect(id: string | null): void;
  onMove(id: string, x: number, y: number): void;
  onLink(from: string, to: string): void;
  onEdge?(edge: GraphEdge): void;
  onCreate?(x: number, y: number): void;
}

const NODE_W = 168;
const NODE_H = 46;
const SVG = 'http://www.w3.org/2000/svg';
/** Pan and zoom survive redraws, per graph (keyed by what it shows). */
const views = new Map<string, { x: number; y: number; k: number }>();

function svg(tag: string, attrs: Record<string, string | number> = {}, text?: string): SVGElement {
  const element = document.createElementNS(SVG, tag);
  for (const [name, value] of Object.entries(attrs)) {
    element.setAttribute(name, String(value));
  }
  if (text !== undefined) {
    element.textContent = text;
  }
  return element;
}

const clip = (text: string, max: number) => (text.length > max ? text.slice(0, max - 1) + '…' : text);

export function graph(viewKey: string, options: GraphOptions): HTMLElement {
  const host = document.createElement('div');
  host.className = 'graph';
  const root = svg('svg', { width: '100%', height: '100%' }) as SVGSVGElement;
  const world = svg('g');
  root.append(world);
  host.append(root);
  const hint = document.createElement('div');
  hint.className = 'graph-hint';
  hint.textContent = options.hint;
  host.append(hint);

  const view = views.get(viewKey) ?? { x: 0, y: 0, k: 1 };
  views.set(viewKey, view);
  const centre = () => ({ x: host.clientWidth / 2 || 400, y: host.clientHeight / 2 || 300 });
  const apply = () => {
    const c = centre();
    world.setAttribute('transform', `translate(${c.x + view.x} ${c.y + view.y}) scale(${view.k})`);
  };
  /** Screen position to graph coordinates. */
  const toWorld = (event: MouseEvent) => {
    const box = root.getBoundingClientRect();
    const c = centre();
    return { x: (event.clientX - box.left - c.x - view.x) / view.k, y: (event.clientY - box.top - c.y - view.y) / view.k };
  };
  const byId = new Map(options.nodes.map((node) => [node.id, node]));

  const edgeLayer = svg('g');
  const nodeLayer = svg('g');
  world.append(edgeLayer, nodeLayer);

  const drawEdges = () => {
    edgeLayer.replaceChildren();
    for (const edge of options.edges) {
      const from = byId.get(edge.from);
      const to = byId.get(edge.to);
      if (!from || !to) {
        continue;
      }
      const x0 = from.x + NODE_W / 2, y0 = from.y, x1 = to.x - NODE_W / 2, y1 = to.y;
      const bend = Math.max(40, Math.abs(x1 - x0) / 2);
      const d = `M ${x0} ${y0} C ${x0 + bend} ${y0}, ${x1 - bend} ${y1}, ${x1} ${y1}`;
      const line = svg('path', { d, class: 'edge' + (edge.dashed ? ' dashed' : '') });
      edgeLayer.append(line);
      if (options.onEdge) {
        // A wide invisible twin makes the thin line easy to click.
        const grab = svg('path', { d, class: 'edge-grab' });
        grab.append(svg('title', {}, 'Click to remove this link'));
        grab.addEventListener('click', () => options.onEdge!(edge));
        edgeLayer.append(grab);
      }
      if (edge.label) {
        edgeLayer.append(svg('text', { x: (x0 + x1) / 2, y: (y0 + y1) / 2 - 6, class: 'edge-label', 'text-anchor': 'middle' }, clip(edge.label, 26)));
      }
    }
  };

  let linking: { from: string; line: SVGElement } | null = null;

  for (const node of options.nodes) {
    const group = svg('g', { class: 'node' + (node.id === options.selected ? ' selected' : ''), transform: `translate(${node.x - NODE_W / 2} ${node.y - NODE_H / 2})` });
    group.append(svg('rect', { width: NODE_W, height: NODE_H, rx: 6 }));
    group.append(svg('rect', { width: 4, height: NODE_H, rx: 2, fill: node.accent ?? '#6A4E18' }));
    group.append(svg('text', { x: 14, y: node.subtitle ? 19 : 28, class: 'node-title' }, clip(node.title, 22)));
    if (node.subtitle) {
      group.append(svg('text', { x: 14, y: 35, class: 'node-sub' }, clip(node.subtitle, 28)));
    }
    const handle = svg('circle', { cx: NODE_W, cy: NODE_H / 2, r: 7, class: 'handle' });
    handle.append(svg('title', {}, 'Drag onto another node to link'));
    group.append(handle);
    group.dataset.id = node.id;

    handle.addEventListener('mousedown', (event) => {
      event.stopPropagation();
      const line = svg('path', { class: 'edge linking' });
      edgeLayer.append(line);
      linking = { from: node.id, line };
    });
    group.addEventListener('mousedown', (event) => {
      if (linking) {
        return;
      }
      event.stopPropagation();
      const start = toWorld(event as MouseEvent);
      const origin = { x: node.x, y: node.y };
      let moved = false;
      const move = (e: MouseEvent) => {
        const now = toWorld(e);
        node.x = Math.round((origin.x + now.x - start.x) / options.snap) * options.snap;
        node.y = Math.round((origin.y + now.y - start.y) / options.snap) * options.snap;
        moved = moved || node.x !== origin.x || node.y !== origin.y;
        group.setAttribute('transform', `translate(${node.x - NODE_W / 2} ${node.y - NODE_H / 2})`);
        drawEdges();
      };
      const up = () => {
        window.removeEventListener('mousemove', move);
        window.removeEventListener('mouseup', up);
        if (moved) {
          options.onMove(node.id, node.x, node.y);
        } else {
          options.onSelect(node.id);
        }
      };
      window.addEventListener('mousemove', move);
      window.addEventListener('mouseup', up);
    });
    nodeLayer.append(group);
  }

  root.addEventListener('mousemove', (event) => {
    if (linking) {
      const from = byId.get(linking.from)!;
      const to = toWorld(event);
      linking.line.setAttribute('d', `M ${from.x + NODE_W / 2} ${from.y} L ${to.x} ${to.y}`);
    }
  });
  root.addEventListener('mouseup', (event) => {
    if (!linking) {
      return;
    }
    const target = (event.target as Element).closest('.node') as SVGElement | null;
    const from = linking.from;
    linking.line.remove();
    linking = null;
    if (target?.dataset.id && target.dataset.id !== from) {
      options.onLink(from, target.dataset.id);
    }
  });
  root.addEventListener('mousedown', (event) => {
    // Background: pan, or deselect on a plain click.
    const start = { x: event.clientX, y: event.clientY, vx: view.x, vy: view.y };
    let moved = false;
    const move = (e: MouseEvent) => {
      view.x = start.vx + e.clientX - start.x;
      view.y = start.vy + e.clientY - start.y;
      moved = true;
      apply();
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
      if (!moved && options.selected) {
        options.onSelect(null);
      }
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  });
  root.addEventListener('dblclick', (event) => {
    if (options.onCreate && !(event.target as Element).closest('.node')) {
      const at = toWorld(event);
      options.onCreate(Math.round(at.x / options.snap) * options.snap, Math.round(at.y / options.snap) * options.snap);
    }
  });
  root.addEventListener('wheel', (event) => {
    event.preventDefault();
    view.k = Math.min(2, Math.max(0.35, view.k * (event.deltaY < 0 ? 1.1 : 0.9)));
    apply();
  }, { passive: false });

  drawEdges();
  // clientWidth is 0 until the element is in the page.
  requestAnimationFrame(apply);
  apply();
  return host;
}
