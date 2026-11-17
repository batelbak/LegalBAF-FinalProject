// מחבר בין הדף, השרת והתצוגה. כל נתון משפטי (טיעונים, משקלים, הכרעה) מגיע מהשרת דרך api.js.
import { api } from './api.js';
import { VIOLATION, categoryLabel, escapeHtml, fmt, summaryOf, titleHtml } from './format.js';
import { HearingPlayer } from './hearing.js';
import { renderGraph } from './graph.js';
import { addEntry, addNotice, addRoundMark, clearRecord, fillEntry, focusEntry, markPruned } from './record.js';
import {
  clearVerdict, pendingTip, renderAssessment, renderBalance, renderTimeline, resetAssessment, setTips, showVerdict,
} from './bench.js';

const $ = id => document.getElementById(id);

const INTRO = 'The prosecution agent argues for the applicant, the defence agent for the Government. '
  + 'Each argument attacks or supports the claim C or an earlier argument; only the bench scores them.';

let caseId = null;
let userSide = 'applicant';
let eventsLoaded = false;
let loadedDemo = false;

// ---------- view: מה שהנגן מצייר ----------
const view = {
  roundMark(n, total) {
    $('round-note').textContent = `Round ${n} of ${total}`;
    addRoundMark(`Round ${n}`);
  },
  drafting(event) {
    const prosecution = event.side === 'applicant';
    const el = addEntry(prosecution ? 'pros' : 'def',
      `${agentName(event.side)} · ${edgesText(event.edges)}`, event.argumentId, null);
    return { fill: () => fillEntry(el, event.text) };
  },
  linking(event) {
    addEntry(event.side === 'applicant' ? 'pros' : 'def', `${agentName(event.side)} · reuses`, event.argumentId,
      `The same argument now also ${escapeHtml(edgesText(event.edges))}. It stays one node, so it is not counted twice.`);
  },
  pruning(event) {
    markPruned(event.argumentId, event.duplicateOf);
    const moved = event.edges.length
      ? ` Its links moved to ${escapeHtml(event.duplicateOf)}: ${escapeHtml(event.edges.map(e => `${e.from} ${e.relation}s ${e.to}`).join(', '))}.`
      : '';
    addEntry('ruling', 'Bench · duplicate', event.argumentId,
      `Restates <span class="mono">${escapeHtml(event.duplicateOf)}</span>, so it is merged into it and not counted twice.${moved} — ${escapeHtml(event.rationale || '')}`);
  },
  update(hearing, enterId) {
    renderGraph($('graph'), hearing, { enterId, onSelect: focusEntry });
    renderBalance(hearing);
    renderTimeline($('timeline'), hearing.history);
  },
  ruling(event) {
    addEntry('ruling', 'Bench', event.argumentId,
      `${escapeHtml(categoryLabel(event.category))} · τ <span class="mono">${fmt(event.baseScore)}</span> · σ <span class="mono">${fmt(event.strengths?.[event.argumentId])}</span> — ${escapeHtml(event.rationale)}`);
    renderAssessment(event);
  },
  status: setStatus,
  close(hearing) {
    showVerdict($('graph-wrap'), hearing.verdict);
    if (loadedDemo) {
      document.querySelector('#graph-wrap .verdict-k').textContent = 'Demo data · not a real judgment';
    }
    setStatus('Hearing closed · judgment delivered', 'done');
    $('round-note').textContent = 'Closed';
    loadTippingPoints();
  },
};

const player = new HearingPlayer(view);

const agentName = side => side === 'applicant' ? 'Prosecution agent · Applicant' : 'Defence agent · Government';
/** "attacks D1, supports C" */
const edgesText = edges => edges.map(e => `${e.relation === 'attack' ? 'attacks' : 'supports'} ${e.to}`).join(', ');

function setStatus(text, cls = '') {
  $('status-text').textContent = text;
  $('status-dot').className = 'dot ' + cls;
}

function setServer(online, text) {
  $('server-dot').className = 'dot ' + (online ? 'online' : 'offline');
  $('server-text').textContent = text;
}

function syncButtons() {
  const ready = caseId != null;
  const done = eventsLoaded && player.finished && !player.busy;
  $('btn-play').disabled = !ready || done;
  $('btn-play').textContent = done ? 'Hearing closed'
    : player.playing ? 'Pause'
      : player.pos > 0 ? 'Resume' : 'Begin hearing';
  $('btn-step').disabled = !ready || done || player.playing;
  $('btn-reset').disabled = !ready;
}

// ---------- case ----------
async function loadCases() {
  try {
    const health = await api.health();
    const ready = health.modelAvailable;
    setServer(true, health.model ? `Server online · ${health.model}${ready ? '' : ' (not running)'}` : 'Server online');
    $('engine').options[0].textContent = `Model · ${health.model || 'LLM'}${ready ? '' : ' (not running)'}`;
    // בלי מודל זמין, ברירת המחדל היא נתוני הדגמה, כדי שיהיה מה לראות
    $('engine').value = ready ? 'model' : 'demo';
    $('graph-note').textContent = `QBAF · attack and support edges · node value = final strength σ (${health.semantics || 'DF-QuAD'})`;
  } catch {
    setServer(false, 'Server offline');
    $('case-title').textContent = 'The server is not running';
    $('case-summary').textContent = 'Start it from the project folder with run-local.cmd';
    return;
  }
  const cases = await api.cases();
  const select = $('case-select');
  select.innerHTML = cases.map(c => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.title)}</option>`).join('');
  select.disabled = !cases.length;
  if (cases.length) await selectCase(cases[0].id);
  else $('case-title').textContent = 'No cases in data/cases';
}

async function selectCase(id) {
  caseId = id;
  const details = await api.caseDetails(id);
  $('case-eyebrow').textContent = `Article 10 · Freedom of expression · HUDOC ${details.id}`;
  $('case-title').innerHTML = titleHtml(details.title);
  $('case-summary').textContent = summaryOf(details.facts);
  $('facts-title').textContent = details.title;
  $('facts-body').textContent = details.facts;
  $('btn-facts').hidden = false;
  reset();
}

function reset() {
  player.load([]);
  eventsLoaded = false;
  clearRecord(INTRO);
  clearVerdict($('graph-wrap'));
  view.update(player.hearing);
  resetAssessment();
  setTips(pendingTip('Shown once the hearing closes.'));
  $('round-note').textContent = 'Round 0';
  setStatus('Ready');
  syncButtons();
}

// ---------- hearing ----------
/** טוען את אירועי הדיון לפי המצב שנבחר. מחזיר false אם השרת לא סיפק דיון. */
let loadingEvents = null;

function ensureEvents() {
  if (eventsLoaded) return Promise.resolve(true);
  // סימולציה חדשה עולה כסף ולוקחת דקות: לחיצה נוספת בזמן הטעינה מחכה לאותה בקשה
  if (!loadingEvents) loadingEvents = fetchEvents().finally(() => { loadingEvents = null; });
  return loadingEvents;
}

async function fetchEvents() {
  const simulation = $('mode').value === 'simulation';
  const demo = $('engine').value === 'demo';
  const requestedCase = caseId;
  setStatus(demo ? 'Preparing demo data…'
    : simulation ? 'The agents are arguing the case… a new simulation can take a few minutes'
      : 'Opening the hearing…', 'live');
  try {
    const events = simulation
      ? (await api.startSimulation(requestedCase, 3, demo)).events
      : await api.hearing(requestedCase, demo);
    // בזמן הטעינה החליפו תיק, מצב או מנוע: לא טוענים דיון שכבר לא רלוונטי
    if (requestedCase !== caseId || simulation !== ($('mode').value === 'simulation')
      || demo !== ($('engine').value === 'demo')) return false;
    player.load(events);
    eventsLoaded = true;
    loadedDemo = demo;
    if (demo) {
      addNotice('Demo data', simulation
        ? 'No model was run. The arguments are placeholders, and who answers whom and every score is generated at random. '
          + 'Only the graph mechanics (attack/support chains, DF-QuAD strengths, the verdict rule) are real.'
        : 'No model was run. These are the real submissions, but which argument answers which and every score are generated at random. '
          + 'Only the graph mechanics are real.');
    }
    return true;
  } catch (e) {
    if (e.notImplemented) {
      addNotice('Agent simulation', 'The live simulation is not available on this server. '
        + 'Choose "Judgment record" to replay the arguments from the judgment.');
      setStatus('Simulation not available');
    } else {
      addNotice(simulation ? 'Agent simulation' : 'Server',
        `${e.message} To see the graph without a model, switch the engine to "Demo data (no model)".`);
      setStatus('Could not open the hearing', '');
    }
    return false;
  }
}

async function play() {
  if (player.playing) {
    player.pause();
    setStatus('Paused');
    syncButtons();
    return;
  }
  if (!(await ensureEvents())) return syncButtons();
  await after(player.play());
}

async function step() {
  if (!(await ensureEvents())) return syncButtons();
  await after(player.step());
}

/** מחכה לנגן; אם בינתיים לחצו Reset, לא דורסים את המצב החדש. */
async function after(run) {
  const generation = player.generation;
  syncButtons();
  await run;
  if (generation !== player.generation) return;
  if (!player.finished) setStatus('Paused');
  syncButtons();
}

async function loadTippingPoints() {
  setTips(pendingTip('Analysing…'));
  try {
    const result = await api.tippingPoints(caseId);
    const tips = result.tips || [];
    setTips(tips.length ? tips.map(t => `<div class="tip">${escapeHtml(t)}</div>`).join('') : pendingTip('No tipping points found.'));
  } catch (e) {
    setTips(pendingTip(e.notImplemented
      ? 'Tipping-point analysis is not connected yet (TippingPointAnalyzer on the server).'
      : e.message));
  }
}

// ---------- litigation strategy ----------
async function submitArgument(text) {
  text = text.trim();
  if (!text) return $('user-arg').focus();
  if (player.busy || player.playing) return;
  $('btn-submit').disabled = true;
  try {
    const replies = await api.counterArguments(caseId, userSide, text);
    addRoundMark('Litigation strategy');
    addEntry(userSide === 'applicant' ? 'pros user' : 'def user', 'Your argument', null, escapeHtml(text));
    for (const reply of replies) {
      const pros = userSide !== 'applicant';
      addEntry(pros ? 'pros' : 'def', pros ? 'Prosecution agent · Applicant' : 'Defence agent · Government',
        null, escapeHtml(reply.text ?? JSON.stringify(reply)));
    }
    $('user-arg').value = '';
  } catch (e) {
    addNotice('Litigation strategy', e.notImplemented
      ? 'Your argument was not entered: the agent that drafts the counter-argument is not connected yet '
        + '(LitigationStrategy on the server). Your text is kept in the box.'
      : e.message);
  } finally {
    $('btn-submit').disabled = false;
  }
}

function setSide(side) {
  userSide = side;
  $('side-app').setAttribute('aria-pressed', String(side === 'applicant'));
  $('side-gov').setAttribute('aria-pressed', String(side === 'government'));
}

// ---------- wiring ----------
$('case-select').addEventListener('change', e => selectCase(e.target.value));
$('mode').addEventListener('change', reset);
$('engine').addEventListener('change', reset);
$('btn-play').addEventListener('click', play);
$('btn-step').addEventListener('click', step);
$('btn-reset').addEventListener('click', reset);
$('speed').addEventListener('change', e => { player.speed = Number(e.target.value) || 1; });
$('side-app').addEventListener('click', () => setSide('applicant'));
$('side-gov').addEventListener('click', () => setSide('government'));
$('composer').addEventListener('submit', e => { e.preventDefault(); submitArgument($('user-arg').value); });
$('btn-facts').addEventListener('click', () => $('facts-dialog').showModal());
$('btn-facts-close').addEventListener('click', () => $('facts-dialog').close());

loadCases().catch(e => {
  setServer(false, 'Server error');
  $('case-title').textContent = 'Could not load the cases';
  $('case-summary').textContent = e.message;
});
