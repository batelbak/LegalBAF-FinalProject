// ציור גרף ה-QBAF: הטענה המרכזית C למעלה, וכל טיעון בשכבה לפי המרחק הקצר שלו מ-C.
// טיעון יכול לענות לכמה קודקודים: הקשת אל ה"הורה" הראשי שלו (היעד הקרוב ביותר ל-C) קובעת את מקומו בפריסה,
// ושאר הקשתות מצוירות כקשתות נוספות. לכל קשת יש חץ אל היעד.
// קשת תמיכה: קו רציף ירוק עם "+". קשת התקפה: קו מקווקו כתום עם "−". עובי הקשת = העוצמה הסופית σ של הטיעון.
// צבע המסגרת של קודקוד = הצד שהעלה אותו. המספר ליד הקודקוד = σ.
import { VIOLATION, NO_VIOLATION, escapeHtml, fmt } from './format.js';

const H = 540, TOP = 64, CLAIM = 'C', CLAIM_W = 240, CLAIM_H = 64;
const SIDE_COLOR = { applicant: 'var(--pros)', government: 'var(--def)' };

/** depth: המרחק הקצר ביותר מ-C. parent: היעד שדרכו עובר המסלול הקצר (הראשון שנמצא). */
function layers(nodes, edges) {
  const depth = { [CLAIM]: 0 };
  const parent = {};
  let changed = true;
  while (changed) {
    changed = false;
    for (const e of edges) {
      if (depth[e.to] == null) continue;
      if (depth[e.from] == null || depth[e.to] + 1 < depth[e.from]) {
        depth[e.from] = depth[e.to] + 1;
        parent[e.from] = e.to;
        changed = true;
      }
    }
  }
  // טיעון שכל היעדים שלו עוד לא מוכרים (לא אמור לקרות): מתחת ל-C
  nodes.forEach(n => { if (depth[n.id] == null) { depth[n.id] = 1; parent[n.id] = CLAIM; } });
  return { depth, parent };
}

/** פריסת עץ לפי ההורה הראשי: כל עלה מקבל עמודה, הורה ממוקם במרכז ילדיו. */
function layout(nodes, edges) {
  const { depth, parent } = layers(nodes, edges);
  const children = { [CLAIM]: [] };
  nodes.forEach(n => { children[n.id] = []; });
  nodes.forEach(n => children[parent[n.id]].push(n.id));

  let leaf = 0;
  const col = {};
  const place = id => {
    if (!children[id].length) { col[id] = leaf++; return col[id]; }
    const cs = children[id].map(place);
    col[id] = (cs[0] + cs[cs.length - 1]) / 2;
    return col[id];
  };
  place(CLAIM);

  const maxDepth = Math.max(1, ...Object.values(depth));
  const leaves = Math.max(1, leaf);
  const width = Math.max(900, leaves * 58 + 80);
  const gapX = (width - 80) / leaves;
  const gapY = Math.min(118, (H - TOP - 150) / maxDepth);
  const pos = {};
  for (const id of Object.keys(col)) {
    pos[id] = { x: 40 + gapX * (col[id] + 0.5), y: TOP + depth[id] * gapY + (id === CLAIM ? 0 : 28) };
  }
  return { pos, parent, width, radius: Math.max(12, Math.min(19, gapX / 3)) };
}

/** הנקודה על שפת היעד שאליה מגיעה קשת שיוצאת מ-from. */
function anchor(target, from, r, isClaim) {
  if (isClaim) {
    const x = Math.max(target.x - CLAIM_W / 2 + 14, Math.min(target.x + CLAIM_W / 2 - 14, from.x));
    return { x, y: target.y + CLAIM_H / 2 };
  }
  const dx = from.x - target.x, dy = from.y - target.y, d = Math.hypot(dx, dy) || 1;
  return { x: target.x + (dx / d) * (r + 3), y: target.y + (dy / d) * (r + 3) };
}

/**
 * hearing: { nodes: [{id, text, side, user}], edges: [{from, to, relation}], baseScores, strengths,
 * claimStrength, leading }. onSelect(id) נקרא בלחיצה על טיעון.
 */
export function renderGraph(svg, hearing, { enterId, onSelect } = {}) {
  const { pos, parent, width, radius: r } = layout(hearing.nodes, hearing.edges);
  svg.setAttribute('viewBox', `0 0 ${width} ${H}`);
  const claim = pos[CLAIM];
  const weighed = id => id === CLAIM || hearing.baseScores[id] != null;
  let paths = '', marks = '', nodes = '';

  for (const e of hearing.edges) {
    const p = pos[e.from], t = pos[e.to];
    if (!p || !t) continue;
    const attack = e.relation === 'attack';
    // קשת "פעילה" כששני הקצוות כבר נשקלו; עד אז היא ממתינה לשופט
    const active = weighed(e.from) && weighed(e.to);
    const primary = parent[e.from] === e.to;
    const end = anchor(t, p, r, e.to === CLAIM);
    let d, mx, my;
    if (primary) {
      const sy = p.y - r, cy = (sy + end.y) / 2;
      d = `M ${p.x} ${sy} C ${p.x} ${cy}, ${end.x} ${cy}, ${end.x} ${end.y}`;
      mx = (p.x + end.x) / 2; my = cy;
    } else {
      // קשת נוספת: קו מעוקל מהשפה של הטיעון אל השפה של היעד, עם קימור הצידה כדי לא לעבור דרך קודקודים
      const start = anchor(p, t, r - 3, false);
      const dx = end.x - start.x, dy = end.y - start.y, len = Math.hypot(dx, dy) || 1;
      const bend = Math.min(60, len * 0.22);
      const cx = (start.x + end.x) / 2 - (dy / len) * bend, cy = (start.y + end.y) / 2 + (dx / len) * bend;
      d = `M ${start.x} ${start.y} Q ${cx} ${cy}, ${end.x} ${end.y}`;
      mx = 0.25 * start.x + 0.5 * cx + 0.25 * end.x; my = 0.25 * start.y + 0.5 * cy + 0.25 * end.y;
    }
    const kind = !active ? 'unweighed' : attack ? 'attack' : 'support';
    const s = hearing.strengths[e.from] ?? 0;
    paths += `<path class="edge ${kind}${primary ? '' : ' extra'}" d="${d}" marker-end="url(#arrow-${kind})"`
      + (active ? ` stroke-width="${(1.2 + s * 4.5).toFixed(2)}"` : '') + '/>';
    const color = attack ? 'var(--attack)' : 'var(--support)';
    marks += `<g class="edge-mark"><title>${escapeHtml(e.from)} ${attack ? 'attacks' : 'supports'} ${escapeHtml(e.to)}</title>`
      + `<circle cx="${mx}" cy="${my}" r="7.5" fill="var(--surface)" stroke="${active ? color : 'var(--unweighed)'}"/>`
      + `<text x="${mx}" y="${my + 4}" text-anchor="middle" font-family="var(--font-mono)" font-weight="700" font-size="12" fill="${color}">${attack ? '−' : '+'}</text></g>`;
  }

  for (const n of hearing.nodes) {
    const p = pos[n.id];
    const isWeighed = hearing.baseScores[n.id] != null;
    const color = SIDE_COLOR[n.side] || 'var(--ink)';
    const sigma = hearing.strengths[n.id];
    const answers = hearing.edges.filter(e => e.from === n.id)
      .map(e => `${e.relation === 'attack' ? 'attacks' : 'supports'} ${e.to}`).join(', ');
    nodes += `<g class="node-arg${n.id === enterId ? ' enter' : ''}" data-id="${escapeHtml(n.id)}" tabindex="0" role="button" aria-label="${escapeHtml(n.id)}">
      <title>${escapeHtml(n.id)} ${escapeHtml(answers)}: ${escapeHtml(n.text)}${isWeighed ? `\nτ = ${fmt(hearing.baseScores[n.id])} · σ = ${fmt(sigma)}` : ''}</title>
      <circle cx="${p.x}" cy="${p.y}" r="${r}" fill="var(--surface)" stroke="${isWeighed ? color : 'var(--unweighed)'}" stroke-width="${n.user ? 3.5 : 2}" ${n.user || !isWeighed ? 'stroke-dasharray="3 2"' : ''}/>
      <text x="${p.x}" y="${p.y + 4}" text-anchor="middle" font-family="var(--font-mono)" font-weight="600" font-size="${r < 15 ? 10 : 12}" fill="${color}">${escapeHtml(n.id)}</text>
      ${isWeighed ? `<text x="${p.x + r + 4}" y="${p.y + 4}" font-family="var(--font-mono)" font-size="10" fill="var(--muted)">${fmt(sigma)}</text>` : ''}
    </g>`;
  }

  const lead = hearing.leading;
  const decided = lead === VIOLATION || lead === NO_VIOLATION;
  const leadColor = lead === VIOLATION ? 'var(--pros)' : lead === NO_VIOLATION ? 'var(--def)' : 'var(--muted)';
  const claimBox = `<g class="claim">
    <title>C: ${escapeHtml(hearing.claimText || 'The interference violated Article 10')}\nBase score τ(C) = 0.50 (neutral). σ(C) above 0.50 means violation, below means no violation.</title>
    <rect x="${claim.x - CLAIM_W / 2}" y="${claim.y - CLAIM_H / 2}" width="${CLAIM_W}" height="${CLAIM_H}" rx="4" fill="var(--surface)" stroke="${decided ? 'var(--brass)' : 'var(--line)'}" stroke-width="${decided ? 2.5 : 1.5}"/>
    ${decided ? `<rect x="${claim.x - CLAIM_W / 2}" y="${claim.y - CLAIM_H / 2}" width="${CLAIM_W}" height="5" fill="var(--brass)"/>` : ''}
    <text x="${claim.x - 106}" y="${claim.y - 8}" font-family="var(--font-body)" font-weight="700" font-size="11" letter-spacing="1.2" fill="var(--muted)">CLAIM C</text>
    <text x="${claim.x - 106}" y="${claim.y + 12}" font-family="var(--font-body)" font-size="12" fill="var(--ink)">Violation of Article 10</text>
    <text x="${claim.x + 106}" y="${claim.y + 9}" text-anchor="end" font-family="var(--font-mono)" font-weight="600" font-size="24" fill="${leadColor}">${fmt(hearing.claimStrength)}</text>
  </g>`;

  const arrow = (id, color) => `<marker id="arrow-${id}" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path d="M 0 1 L 9 5 L 0 9 z" fill="${color}"/></marker>`;
  const defs = `<defs>${arrow('support', 'var(--support)')}${arrow('attack', 'var(--attack)')}${arrow('unweighed', 'var(--unweighed)')}</defs>`;

  const empty = hearing.nodes.length ? '' :
    `<text x="${width / 2}" y="${claim.y + 110}" text-anchor="middle" font-family="var(--font-display)" font-size="14" fill="var(--muted)">Arguments will attach here, each one attacking or supporting C and/or other arguments</text>`;

  svg.innerHTML = defs + paths + marks + claimBox + nodes + empty;
  svg.querySelectorAll('.node-arg').forEach(g => {
    const go = () => onSelect?.(g.dataset.id);
    g.addEventListener('click', go);
    g.addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
  });
}
