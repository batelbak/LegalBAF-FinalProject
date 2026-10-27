// שמות לתצוגה ועיצוב מספרים. המזהים זהים לאלה שבבאקנד (ArgumentCategory, LegalCase).

export const VIOLATION = 'violation';
export const NO_VIOLATION = 'no_violation';

export const CATEGORY_LABELS = {
  public_interest: 'Public interest',
  status_of_person: 'Status of person',
  prior_conduct: 'Prior conduct',
  truth_and_verification: 'Truth & verification',
  content_and_form: 'Content & form',
  severity_of_sanction: 'Severity of sanction',
  lawfulness_and_aim: 'Lawfulness & aim',
  other: 'Other',
};

export const categoryLabel = id => CATEGORY_LABELS[id] || id;

export const decisionName = d => d === VIOLATION ? 'Violation of Article 10'
  : d === NO_VIOLATION ? 'No violation of Article 10' : 'Undecided';

export const fmt = x => Number(x || 0).toFixed(2);

export function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

/** "Tammer v. Estonia" → כותרת עם v. נטוי. */
export const titleHtml = title => escapeHtml(title).replace(/ v\. /, ' <em>v.</em> ');

/** הפסקה הראשונה של העובדות, מקוצרת. */
export function summaryOf(facts, max = 320) {
  const first = facts.split(/\n\s*\n/)[0].trim();
  if (first.length <= max) return first;
  const cut = first.slice(0, max);
  return cut.slice(0, cut.lastIndexOf(' ')) + '…';
}
