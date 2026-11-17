// מנגן את אירועי הדיון שהשרת החזיר, אחד אחרי השני, ומעדכן את מצב התצוגה.
// לא מחשב עוצמות: σ ו-leading מגיעים מהשרת בכל אירוע weigh / link / prune.
import { VIOLATION, NO_VIOLATION } from './format.js';

export function emptyHearing() {
  return {
    nodes: [],                       // { id, text, side, user }
    edges: [],                       // { from, to, relation } — טיעון יכול לענות לכמה קודקודים
    baseScores: {},                  // id → τ שהשופט קבע
    strengths: {},                   // id → σ לפי הסמנטיקה
    claimStrength: 0.5,              // σ(C)
    leading: null,
    history: [],                     // { id, m = σ(C) − 0.5, flip, decisive } לגרף הזמן
    pruned: {},                      // כפיל → המקור שאליו מוזג
    round: 0,
    verdict: null,
  };
}

const DRAFT_MS = 1100, ENTER_MS = 650, WEIGH_MS = 500, AFTER_WEIGH_MS = 700, ROUND_MS = 400;

/**
 * view מקבל:
 * roundMark(n, total), drafting(event) → { fill() }, linking(event), update(hearing, enterId),
 * ruling(event), pruning(event), status(text, cls), close(hearing)
 */
export class HearingPlayer {
  constructor(view) {
    this.view = view;
    this.speed = 1;
    this.load([]);
  }

  load(events) {
    clearTimeout(this.timer);
    this.generation = (this.generation || 0) + 1;
    this.events = events;
    this.pos = 0;
    this.playing = false;
    this.busy = false;
    this.hearing = emptyHearing();
    this.totalRounds = Math.max(0, ...events.map(e => e.round || 0));
  }

  get finished() {
    return this.pos >= this.events.length;
  }

  wait(ms) {
    const generation = this.generation;
    return new Promise((resolve, reject) => {
      this.timer = setTimeout(() => generation === this.generation ? resolve() : reject(new Error('reset')), ms / this.speed);
    });
  }

  async play() {
    if (this.busy) return;
    this.playing = true;
    try {
      while (this.playing && !this.finished) await this.next();
    } catch (e) {
      if (e.message !== 'reset') throw e;
    }
    this.playing = false;
  }

  pause() {
    this.playing = false;
  }

  async step() {
    if (this.busy || this.playing || this.finished) return;
    try {
      await this.next();
    } catch (e) {
      if (e.message !== 'reset') throw e;
    }
  }

  async next() {
    this.busy = true;
    const generation = this.generation;
    try {
      await this.apply(this.events[this.pos++]);
    } finally {
      if (generation === this.generation) this.busy = false;
    }
  }

  /** מעדכן את ההכרעה מהאירוע ורושם נקודה בגרף הזמן. */
  rule(event) {
    const h = this.hearing;
    const decided = event.leading === VIOLATION || event.leading === NO_VIOLATION;
    const flip = decided && event.leading !== h.lastLead;
    if (decided) h.lastLead = event.leading;
    h.strengths = event.strengths;
    h.claimStrength = event.claimStrength;
    h.leading = event.leading;
    h.history.push({ id: event.argumentId, m: event.claimStrength - 0.5, flip });
    h.history.forEach(x => { x.decisive = false; });
    const lastFlip = [...h.history].reverse().find(x => x.flip);
    if (lastFlip) lastFlip.decisive = true;
  }

  async apply(event) {
    const h = this.hearing;
    if (event.round && event.round !== h.round) {
      h.round = event.round;
      this.view.roundMark(event.round, this.totalRounds);
      await this.wait(ROUND_MS);
    }
    const prosecution = event.side === 'applicant';
    if (event.type === 'present') {
      this.view.status(prosecution ? 'Prosecution agent is drafting…' : 'Defence agent is drafting…', 'live');
      const entry = this.view.drafting(event);
      await this.wait(DRAFT_MS);
      entry.fill();
      h.nodes.push({ id: event.argumentId, text: event.text, side: event.side });
      h.edges.push(...event.edges);
      this.view.update(h, event.argumentId);
      this.view.status('Argument entered · awaiting the bench', 'live');
      await this.wait(ENTER_MS);
    } else if (event.type === 'link') {
      // שימוש חוזר: טיעון קיים מכוון אל יעדים נוספים. הוא כבר נשקל, ולכן ההכרעה מתעדכנת מיד
      this.view.status(`${prosecution ? 'Prosecution' : 'Defence'} agent reuses ${event.argumentId}…`, 'live');
      await this.wait(WEIGH_MS);
      h.edges.push(...event.edges);
      this.rule(event);
      this.view.linking(event);
      this.view.update(h, event.argumentId);
      await this.wait(AFTER_WEIGH_MS);
    } else if (event.type === 'weigh') {
      this.view.status(`The bench is weighing ${event.argumentId}…`, 'live');
      await this.wait(WEIGH_MS);
      h.baseScores[event.argumentId] = event.baseScore;
      this.rule(event);
      this.view.ruling(event);
      this.view.update(h);
      await this.wait(AFTER_WEIGH_MS);
    } else if (event.type === 'prune') {
      // השופט קבע שהטיעון חוזר על טיעון קודם: הקודקוד נמחק, והקשתות שלו עוברות אל המקור
      this.view.status(`The bench merges ${event.argumentId} into ${event.duplicateOf}…`, 'live');
      await this.wait(WEIGH_MS);
      h.nodes = h.nodes.filter(n => n.id !== event.argumentId);
      h.edges = h.edges.filter(e => e.from !== event.argumentId && e.to !== event.argumentId);
      h.edges.push(...event.edges);
      h.pruned[event.argumentId] = event.duplicateOf;
      this.rule(event);
      this.view.pruning(event);
      this.view.update(h, event.duplicateOf);
      await this.wait(AFTER_WEIGH_MS);
    } else if (event.type === 'verdict') {
      h.verdict = event;
      this.view.close(h);
    }
  }
}
