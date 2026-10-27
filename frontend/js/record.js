// פרוטוקול הדיון (החלון השמאלי).
import { escapeHtml } from './format.js';

const record = () => document.getElementById('record');

export function clearRecord(text) {
  record().innerHTML = `<p class="empty">${escapeHtml(text)}</p>`;
}

function append(el) {
  record().querySelector('.empty')?.remove();
  record().appendChild(el);
  record().scrollTop = record().scrollHeight;
  return el;
}

export function addRoundMark(text) {
  const el = document.createElement('div');
  el.className = 'round-mark';
  el.textContent = text;
  return append(el);
}

/** bodyHtml == null: הסוכן עוד מנסח, מוצגות נקודות הקלדה עד fillEntry. */
export function addEntry(cls, head, argumentId, bodyHtml) {
  const el = document.createElement('div');
  el.className = 'entry ' + cls;
  el.innerHTML = `<div class="entry-head">${escapeHtml(head)}${argumentId ? ` <span class="entry-id">${escapeHtml(argumentId)}</span>` : ''}</div>` +
    (bodyHtml == null ? '<div class="typing" aria-label="drafting"><i></i><i></i><i></i></div>' : `<p class="entry-text">${bodyHtml}</p>`);
  if (argumentId) el.dataset.id = argumentId;
  return append(el);
}

export function fillEntry(el, text) {
  el.querySelector('.typing').outerHTML = `<p class="entry-text">${escapeHtml(text)}</p>`;
  record().scrollTop = record().scrollHeight;
}

export function addNotice(head, text) {
  return addEntry('notice', head, null, escapeHtml(text));
}

export function focusEntry(argumentId) {
  const el = record().querySelector(`.entry[data-id="${CSS.escape(argumentId)}"]`);
  if (!el) return;
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  el.classList.add('flash');
  setTimeout(() => el.classList.remove('flash'), 1400);
}
