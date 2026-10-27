// מנגן את אירועי הדיון שהשרת החזיר, אחד אחרי השני, ומעדכן את מצב התצוגה.
// לא מחשב ציונים: scores ו-leading מגיעים מהשופט בשרת בכל אירוע weigh.
import { VIOLATION, NO_VIOLATION } from './format.js';

export function emptyHearing() {
  return {
    nodes: [],                       // { id, text, decision, user }
    weights: {},                     // id → משקל הקשת שהשופט קבע
    scores: { [VIOLATION]: 0, [NO_VIOLATION]: 0 },
    leading: null,
    history: [],                     // { id, m, flip, decisive } לגרף הפער
    round: 0,
    verdict: null,
  };
}

const DRAFT_MS = 1100, ENTER_MS = 650, WEIGH_MS = 500, AFTER_WEIGH_MS = 700, ROUND_MS = 400;

/**
 * view מקבל:
 * roundMark(n, total), drafting(event) → { fill() }, update(hearing, enterId),
 * ruling(event), status(text, cls), close(hearing)
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

  async apply(event) {
    const h = this.hearing;
    if (event.round && event.round !== h.round) {
      h.round = event.round;
      this.view.roundMark(event.round, this.totalRounds);
      await this.wait(ROUND_MS);
    }
    if (event.type === 'present') {
      const prosecution = event.decision === VIOLATION;
      this.view.status(prosecution ? 'Prosecution agent is drafting…' : 'Defence agent is drafting…', 'live');
      const entry = this.view.drafting(event);
      await this.wait(DRAFT_MS);
      entry.fill();
      h.nodes.push({ id: event.argumentId, text: event.text, decision: event.decision });
      this.view.update(h, event.argumentId);
      this.view.status('Argument entered · edge awaiting the bench', 'live');
      await this.wait(ENTER_MS);
    } else if (event.type === 'weigh') {
      this.view.status(`The bench is weighing ${event.argumentId}…`, 'live');
      await this.wait(WEIGH_MS);
      const decided = event.leading === VIOLATION || event.leading === NO_VIOLATION;
      const flip = decided && event.leading !== h.lastLead;
      if (decided) h.lastLead = event.leading;
      h.weights[event.argumentId] = event.weight;
      h.scores = event.scores;
      h.leading = event.leading;
      h.history.push({ id: event.argumentId, m: event.scores[VIOLATION] - event.scores[NO_VIOLATION], flip });
      h.history.forEach(x => { x.decisive = false; });
      const lastFlip = [...h.history].reverse().find(x => x.flip);
      if (lastFlip) lastFlip.decisive = true;
      this.view.ruling(event);
      this.view.update(h);
      await this.wait(AFTER_WEIGH_MS);
    } else if (event.type === 'verdict') {
      h.verdict = event;
      this.view.close(h);
    }
  }
}
