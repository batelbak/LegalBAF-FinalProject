// מחבר בין הדף, השרת והתצוגה. כל נתון משפטי (טיעונים, משקלים, הכרעה) מגיע מהשרת דרך api.js.
import { api } from './api.js';
import { VIOLATION, categoryLabel, escapeHtml, fmt, summaryOf, titleHtml } from './format.js';
import { HearingPlayer } from './hearing.js';
import { renderGraph } from './graph.js';
import { addEntry, addNotice, addRoundMark, clearRecord, fillEntry, focusEntry } from './record.js';
import {
  clearVerdict, pendingTip, renderAssessment, renderBalance, renderTimeline, resetAssessment, setTips, showVerdict,
} from './bench.js';

const $ = id => document.getElementById(id);

const INTRO = 'The prosecution agent opens for the applicant. The defence agent answers for the Government. '
  + 'Each argument is entered into the graph by the agent who raised it; only the bench sets the weights.';

let caseId = null;
let userSide = 'applicant';
let eventsLoaded = false;

// ---------- view: מה שהנגן מצייר ----------
const view = {
  roundMark(n, total) {
    $('round-note').textContent = `Round ${n} of ${total}`;
    addRoundMark(`Round ${n}`);
  },
  drafting(event) {
    const prosecution = event.decision === VIOLATION;
    const el = addEntry(prosecution ? 'pros' : 'def',
      prosecution ? 'Prosecution agent · Applicant' : 'Defence agent · Government', event.argumentId, null);
    return { fill: () => fillEntry(el, event.text) };
  },
  update(hearing, enterId) {
    renderGraph($('graph'), hearing, { enterId, onSelect: focusEntry });
    renderBalance(hearing);
    renderTimeline($('timeline'), hearing.history);
  },
  ruling(event) {
    addEntry('ruling', 'Bench', event.argumentId,
      `${escapeHtml(categoryLabel(event.category))} · strength <span class="mono">${fmt(event.strength)}</span> — ${escapeHtml(event.rationale)}`);
    renderAssessment(event);
  },
  status: setStatus,
  close(hearing) {
    showVerdict($('graph-wrap'), hearing.verdict);
    setStatus('Hearing closed · judgment delivered', 'done');
    $('round-note').textContent = 'Closed';
    loadTippingPoints();
  },
};

const player = new HearingPlayer(view);

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
    await api.health();
    setServer(true, 'Server online · stage A');
  } catch {
    setServer(false, 'Server offline');
    $('case-title').textContent = 'The server is not running';
    $('case-summary').textContent = 'Start it from the project folder: mvn compile exec:java -Dexec.mainClass=ac.qbaf.legalsim.web.WebServer';
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
async function ensureEvents() {
  if (eventsLoaded) return true;
  setStatus('Opening the hearing…', 'live');
  try {
    const events = $('mode').value === 'simulation'
      ? (await api.startSimulation(caseId, 3)).events
      : await api.hearing(caseId);
    player.load(events);
    eventsLoaded = true;
    return true;
  } catch (e) {
    if (e.notImplemented) {
      addNotice('Agent simulation', 'The live simulation is not connected yet: the prosecution and defence agents '
        + 'cannot draft new arguments until TrialSimulation is implemented on the server. '
        + 'Choose "Judgment record" to replay the arguments from the judgment.');
      setStatus('Simulation not available yet');
    } else {
      addNotice('Server', e.message);
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
