// לוח השופט (החלון הימני) ותיבת פסק הדין על הגרף. מציג רק ערכים שהגיעו מהשרת.
import { VIOLATION, NO_VIOLATION, categoryLabel, decisionName, escapeHtml, fmt } from './format.js';

const $ = id => document.getElementById(id);

export function renderBalance(hearing) {
  const p = hearing.scores[VIOLATION] || 0;
  const d = hearing.scores[NO_VIOLATION] || 0;
  const total = p + d;
  const share = total ? (p / total) * 100 : 50;
  $('bar-p').style.width = share + '%';
  $('bar-d').style.width = (100 - share) + '%';
  $('score-p').textContent = fmt(p);
  $('score-d').textContent = fmt(d);
  if (!hearing.history.length) {
    $('lead').textContent = 'No arguments weighed yet.';
  } else if (hearing.leading === VIOLATION || hearing.leading === NO_VIOLATION) {
    $('lead').innerHTML = `<strong>${decisionName(hearing.leading)}</strong> leads by <span class="mono">${fmt(Math.abs(p - d))}</span>.`;
  } else {
    $('lead').innerHTML = '<strong>Undecided.</strong> The two decisions are level.';
  }
}

export function resetAssessment() {
  $('assess').innerHTML = '<p>The bench assigns each argument a category and a strength from 0 to 1. The parties never set their own weights.</p>';
}

/** event: אירוע weigh מהשרת. */
export function renderAssessment(event) {
  $('assess').innerHTML = `<div class="assess-row"><span class="mono">${escapeHtml(event.argumentId)}</span><span class="chip">${escapeHtml(categoryLabel(event.category))}</span></div>
    <div class="strength"><i style="width:${event.strength * 100}%"></i></div>
    <div class="assess-row" style="margin-bottom:6px"><span class="muted">Strength</span><span class="mono">${fmt(event.strength)}</span></div>
    <p>${escapeHtml(event.rationale)}</p>`;
}

/** history: [{ id, m, flip, decisive }], m = violation − no_violation אחרי כל שקילה. */
export function renderTimeline(svg, history) {
  const W = 300, H = 130, pad = 8, mid = H / 2;
  const maxAbs = Math.max(1, ...history.map(x => Math.abs(x.m)));
  const xs = i => pad + (history.length <= 1 ? 0 : (i * (W - 2 * pad)) / (history.length - 1));
  const ys = m => mid - (m / maxAbs) * (mid - 14);
  let out = `<text x="${pad}" y="11" font-size="10" font-family="var(--font-body)" fill="var(--pros)">Violation ahead</text>
    <text x="${pad}" y="${H - 3}" font-size="10" font-family="var(--font-body)" fill="var(--def)">No violation ahead</text>
    <line x1="${pad}" x2="${W - pad}" y1="${mid}" y2="${mid}" stroke="var(--line)" stroke-dasharray="3 3"/>`;
  if (history.length) {
    out += `<polyline points="${history.map((x, i) => `${xs(i)},${ys(x.m)}`).join(' ')}" fill="none" stroke="var(--ink)" stroke-width="1.6"/>`;
    history.forEach((x, i) => {
      if (x.flip) out += `<circle cx="${xs(i)}" cy="${ys(x.m)}" r="${x.decisive ? 5 : 3}" fill="${x.decisive ? 'var(--brass)' : 'var(--surface)'}" stroke="var(--brass)" stroke-width="1.5"/>`;
    });
    const last = history[history.length - 1];
    out += `<circle cx="${xs(history.length - 1)}" cy="${ys(last.m)}" r="3.5" fill="var(--ink)"/>`;
    const decisive = history.find(x => x.decisive);
    if (decisive) {
      const i = history.indexOf(decisive);
      const tx = Math.min(W - 60, Math.max(40, xs(i)));
      const ty = ys(decisive.m) - 9 < 22 ? ys(decisive.m) + 17 : ys(decisive.m) - 9;
      out += `<text x="${tx}" y="${ty}" text-anchor="middle" font-size="10" font-weight="700" font-family="var(--font-body)" fill="var(--brass)">Lead settled · ${escapeHtml(decisive.id)}</text>`;
    }
  }
  svg.innerHTML = out;
}

export function setTips(html) {
  $('tips').innerHTML = html;
}

export const pendingTip = text => `<div class="tip pending">${escapeHtml(text)}</div>`;

/** verdict: אירוע verdict מהשרת. */
export function showVerdict(wrap, verdict) {
  let box = wrap.querySelector('.verdict');
  if (!box) {
    box = document.createElement('div');
    box.className = 'verdict';
    wrap.appendChild(box);
  }
  const match = verdict.predicted === verdict.actual;
  box.innerHTML = `<div class="verdict-k">Judgment of the simulated court</div>
    <div class="verdict-v">${decisionName(verdict.predicted)}</div>
    <div class="verdict-s"><span class="mono">${fmt(verdict.scores[VIOLATION])}</span> violation · <span class="mono">${fmt(verdict.scores[NO_VIOLATION])}</span> no violation<br>
    Strasbourg Court's actual judgment: <strong>${decisionName(verdict.actual)}</strong> ${match ? '✓' : '✗'}</div>`;
}

export function clearVerdict(wrap) {
  wrap.querySelector('.verdict')?.remove();
}
