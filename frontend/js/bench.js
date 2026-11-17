// לוח השופט (החלון הימני) ותיבת פסק הדין על הגרף. מציג רק ערכים שהגיעו מהשרת.
import { VIOLATION, NO_VIOLATION, categoryLabel, decisionName, escapeHtml, fmt } from './format.js';

const $ = id => document.getElementById(id);

/** σ(C) על סרגל 0–1: החלק האדום עד σ(C), הכחול מעליו. הסף הוא ציון הבסיס הניטרלי 0.5. */
export function renderBalance(hearing) {
  const c = hearing.claimStrength ?? 0.5;
  $('bar-p').style.width = c * 100 + '%';
  $('bar-d').style.width = (1 - c) * 100 + '%';
  $('score-p').textContent = fmt(c);
  $('score-d').textContent = fmt(0.5);
  if (!hearing.history.length) {
    $('lead').textContent = 'No arguments weighed yet. C starts at its neutral base score 0.50.';
  } else if (hearing.leading === VIOLATION || hearing.leading === NO_VIOLATION) {
    $('lead').innerHTML = `<strong>${decisionName(hearing.leading)}</strong> · σ(C) <span class="mono">${fmt(c)}</span> ${c > 0.5 ? '&gt;' : '&lt;'} 0.50`;
  } else {
    $('lead').innerHTML = '<strong>Undecided.</strong> The attacks and supports on C balance out.';
  }
}

export function resetAssessment() {
  $('assess').innerHTML = '<p>The bench gives each argument a category and a base score τ from 0 to 1. Its final strength σ then follows from what attacks and supports it (DF-QuAD). The parties never score their own arguments.</p>';
}

/** event: אירוע weigh מהשרת. */
export function renderAssessment(event) {
  if (event.type !== 'weigh') return;
  $('assess').innerHTML = `<div class="assess-row"><span class="mono">${escapeHtml(event.argumentId)}</span><span class="chip">${escapeHtml(categoryLabel(event.category))}</span></div>
    <div class="strength"><i style="width:${event.baseScore * 100}%"></i></div>
    <div class="assess-row"><span class="muted">Base score τ</span><span class="mono">${fmt(event.baseScore)}</span></div>
    <div class="assess-row" style="margin-bottom:6px"><span class="muted">Final strength σ</span><span class="mono">${fmt(event.strengths?.[event.argumentId])}</span></div>
    <p>${escapeHtml(event.rationale)}</p>`;
}

/** history: [{ id, m, flip, decisive }], m = σ(C) − 0.5 אחרי כל שקילה. */
export function renderTimeline(svg, history) {
  const W = 300, H = 130, pad = 8, mid = H / 2;
  const maxAbs = 0.5;
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
  // אותה שאלה תחת שלוש הסמנטיקות שבמאמר: אם ההכרעה משתנה ביניהן, התיק שביר
  const others = Object.values(verdict.alternatives || {})
    .map(a => `${escapeHtml(a.name)} <span class="mono">${fmt(a.claimStrength)}</span>${a.leading === verdict.predicted ? '' : ' ≠'}`)
    .join(' · ');
  box.innerHTML = `<div class="verdict-k">Judgment of the simulated court</div>
    <div class="verdict-v">${decisionName(verdict.predicted)}</div>
    <div class="verdict-s">σ(C) = <span class="mono">${fmt(verdict.claimStrength)}</span> under ${escapeHtml(verdict.semantics || 'DF-QuAD')} (above 0.50: violation)<br>
    ${others ? `<span class="muted">σ(C) by semantics: ${others}</span><br>` : ''}
    Strasbourg Court's actual judgment: <strong>${decisionName(verdict.actual)}</strong> ${match ? '✓' : '✗'}</div>`;
}

export function clearVerdict(wrap) {
  wrap.querySelector('.verdict')?.remove();
}
