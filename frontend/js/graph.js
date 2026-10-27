// ציור גרף ה-QBAF: טיעוני התביעה משמאל, טיעוני ההגנה מימין, שני קודקודי ההחלטה במרכז.
// קשתות תמיכה בלבד, מטיעון אל ההחלטה שהוא תומך בה. עובי הקשת = המשקל שהשופט קבע.
import { VIOLATION, NO_VIOLATION, escapeHtml, fmt } from './format.js';

const W = 900, H = 540, CY = H / 2;
const DECISIONS = {
  [VIOLATION]: { x: 335, y: CY, label: 'VIOLATION', color: 'var(--pros)' },
  [NO_VIOLATION]: { x: 565, y: CY, label: 'NO VIOLATION', color: 'var(--def)' },
};

function bezierPoint(p0, p1, p2, p3, t) {
  const u = 1 - t;
  return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
}

function layout(nodes) {
  const place = (list, x) => list.map((n, i) => {
    const gap = Math.min(52, (H - 90) / Math.max(1, list.length - 1));
    const total = gap * (list.length - 1);
    return { ...n, x, y: CY + 6 - total / 2 + i * gap };
  });
  return [
    ...place(nodes.filter(n => n.decision === VIOLATION), 70),
    ...place(nodes.filter(n => n.decision === NO_VIOLATION), 830),
  ];
}

/** hearing: { nodes, weights, scores, leading }. onSelect(id) נקרא בלחיצה על טיעון. */
export function renderGraph(svg, hearing, { enterId, onSelect } = {}) {
  let edges = '', labels = '', nodes = '';
  for (const n of layout(hearing.nodes)) {
    const target = DECISIONS[n.decision];
    const tx = n.decision === VIOLATION ? target.x - 78 : target.x + 78;
    const c = n.x + (tx - n.x) * 0.5;
    const d = `M ${n.x} ${n.y} C ${c} ${n.y}, ${c} ${target.y}, ${tx} ${target.y}`;
    const w = hearing.weights[n.id];
    if (w == null) {
      edges += `<path class="edge unweighed" d="${d}"/>`;
    } else {
      edges += `<path class="edge" d="${d}" stroke="${target.color}" stroke-opacity="0.75" stroke-width="${(1 + w * 7).toFixed(2)}"/>`;
      const mx = bezierPoint(n.x, c, c, tx, 0.42), my = bezierPoint(n.y, n.y, target.y, target.y, 0.42);
      labels += `<g><rect x="${mx - 17}" y="${my - 10}" width="34" height="18" rx="3" fill="var(--surface)" stroke="var(--line)"/>` +
        `<text x="${mx}" y="${my + 3.5}" text-anchor="middle" font-family="var(--font-mono)" font-size="11" fill="var(--ink)">${fmt(w)}</text></g>`;
    }
    const user = n.user;
    nodes += `<g class="node-arg${n.id === enterId ? ' enter' : ''}" data-id="${escapeHtml(n.id)}" tabindex="0" role="button" aria-label="${escapeHtml(n.id)}">
      <title>${escapeHtml(n.id)}: ${escapeHtml(n.text)}</title>
      <circle cx="${n.x}" cy="${n.y}" r="19" fill="var(--surface)" stroke="${target.color}" stroke-width="${user ? 3.5 : 2}" ${user ? 'stroke-dasharray="3 2"' : ''}/>
      <text x="${n.x}" y="${n.y + 4}" text-anchor="middle" font-family="var(--font-mono)" font-weight="600" font-size="12" fill="${target.color}">${escapeHtml(n.id)}</text>
    </g>`;
  }

  const decisionBox = key => {
    const p = DECISIONS[key];
    const on = hearing.leading === key;
    return `<g>
      <rect x="${p.x - 78}" y="${p.y - 52}" width="156" height="104" rx="4" fill="var(--surface)" stroke="${on ? 'var(--brass)' : p.color}" stroke-width="${on ? 3 : 1.5}"/>
      ${on ? `<rect x="${p.x - 78}" y="${p.y - 52}" width="156" height="6" fill="var(--brass)"/>` : ''}
      <text x="${p.x}" y="${p.y - 24}" text-anchor="middle" font-family="var(--font-body)" font-weight="700" font-size="12" letter-spacing="1.5" fill="${p.color}">${p.label}</text>
      <text x="${p.x}" y="${p.y - 8}" text-anchor="middle" font-family="var(--font-body)" font-size="11" fill="var(--muted)">of Article 10</text>
      <text x="${p.x}" y="${p.y + 28}" text-anchor="middle" font-family="var(--font-mono)" font-weight="600" font-size="26" fill="var(--ink)">${fmt(hearing.scores[key])}</text>
    </g>`;
  };

  const header = `
    <text x="70" y="14" text-anchor="middle" font-family="var(--font-body)" font-size="11" font-weight="700" letter-spacing="1.2" fill="var(--pros)">PROSECUTION</text>
    <text x="830" y="14" text-anchor="middle" font-family="var(--font-body)" font-size="11" font-weight="700" letter-spacing="1.2" fill="var(--def)">DEFENCE</text>
    <text x="${W / 2}" y="${CY + 92}" text-anchor="middle" font-family="var(--font-display)" font-size="13" fill="var(--muted)">Decision nodes</text>`;

  svg.innerHTML = header + edges + labels + decisionBox(VIOLATION) + decisionBox(NO_VIOLATION) + nodes;
  svg.querySelectorAll('.node-arg').forEach(g => {
    const go = () => onSelect?.(g.dataset.id);
    g.addEventListener('click', go);
    g.addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
  });
}
