package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.PartyArgument;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.PartyAgent;
import ac.qbaf.legalsim.agents.PartyAgent.Draft;
import ac.qbaf.legalsim.agents.PartyAgent.Turn;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafGraph.Edge;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Alternative;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Ruling;
import ac.qbaf.legalsim.report.GraphReport;
import ac.qbaf.legalsim.simulation.RecordedHearing.Linked;
import ac.qbaf.legalsim.simulation.RecordedHearing.Presented;
import ac.qbaf.legalsim.simulation.RecordedHearing.Pruned;
import ac.qbaf.legalsim.simulation.RecordedHearing.Verdict;
import ac.qbaf.legalsim.simulation.RecordedHearing.Weighed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * סימולציה אדברסרית: סוכני התביעה וההגנה מנסחים טיעונים בעצמם מתוך העובדות בלבד, בסבבים,
 * ובונים יחד גרף QBAF שבשורשו הטענה המרכזית C.
 * <p>
 * בכל סבב התביעה פועלת ואז ההגנה, שרואה את מה שהתביעה הוסיפה. כל פריט במהלך הוא אחד משניים:
 * <ul>
 *   <li>טיעון חדש (P1, P2... / D1, D2...) שעונה לקודקוד אחד או יותר;</li>
 *   <li>שימוש חוזר: טיעון קיים של הסוכן מכוון אל יעדים נוספים. הטיעון כבר נשקל, ולכן ההכרעה מתעדכנת מיד.</li>
 * </ul>
 * בסוף כל סבב השופט מעריך רק את הטיעונים החדשים (ההערכות הקודמות נשמרות). טיעון שהוא קובע שחוזר בדיוק על
 * טיעון קודם של אותו צד ממוזג אליו ולא נספר פעמיים; לכל טיעון אחר נקבע ציון בסיס. אחרי כל צעד ההכרעה
 * מחושבת על הטיעונים שכבר נשקלו (QbafGraph.scoredSubgraph), וכך מתקבל ציר זמן של σ(C).
 * <p>
 * האירועים באותו פורמט של RecordedHearing, כך שהפרונטאנד מנגן את שניהם באותה דרך.
 */
public class TrialSimulation {
    public static final int DEFAULT_ROUNDS = 3;
    public static final int MAX_ROUNDS = 6;

    /** מצב הדיון בסוף סבב, אחרי שהשופט שקל את הטיעונים החדשים. arguments: כמה טיעונים יש בגרף. */
    public record RoundState(int round, int arguments, double claimStrength, String leading) {
    }

    /**
     * כל מה שהסימולציה הפיקה. נשמר כקובץ כדי לנתח בלי להריץ שוב.
     * pruned: טיעונים שמוזגו (כפיל → מקור). semantics: הסמנטיקה שלפיה הוכרע; alternatives: ההכרעה תחת כל השלוש.
     */
    public record Result(String caseId, String title, int rounds, int maxArguments, List<Object> events,
                         List<Turn> transcript, List<RoundState> timeline, List<ScoredArgument> assessment,
                         Map<String, String> pruned, double claimStrength, Map<String, Double> strengths,
                         String predicted, String actual, String semantics, Map<String, Alternative> alternatives,
                         SimulationComparison.Comparison comparison, GraphReport.Report graph) {
    }

    private final ProsecutorAgent prosecutor;
    private final DefenseAgent defense;
    private final JudgeAgent judge;

    public TrialSimulation() {
        this(new ProsecutorAgent(), new DefenseAgent(), new JudgeAgent());
    }

    /** סוכנים עם llm מזויף לטסטים. */
    public TrialSimulation(ProsecutorAgent prosecutor, DefenseAgent defense, JudgeAgent judge) {
        this.prosecutor = prosecutor;
        this.defense = defense;
        this.judge = judge;
    }

    public Result run(LegalCase legalCase, int rounds) {
        return run(legalCase, rounds, PartyAgent.DEFAULT_MAX_ARGUMENTS, null);
    }

    /**
     * recorded: גרף הטענות האמיתיות עם ציוני השופט השמורים (RecordedHearing.weighedGraph), להשוואה.
     * null אם אין.
     */
    public Result run(LegalCase legalCase, int rounds, int maxArguments, QbafGraph recorded) {
        if (rounds < 1 || rounds > MAX_ROUNDS) {
            throw new IllegalArgumentException("rounds must be between 1 and " + MAX_ROUNDS + ", got " + rounds);
        }
        if (maxArguments < 1) {
            throw new IllegalArgumentException("maxArguments must be positive, got " + maxArguments);
        }
        String facts = legalCase.facts();
        QbafGraph graph = LegalCase.emptyGraph();

        List<Turn> transcript = new ArrayList<>();
        Map<String, Integer> turnOf = new HashMap<>();
        List<ScoredArgument> assessment = new ArrayList<>();
        List<Object> events = new ArrayList<>();
        List<RoundState> timeline = new ArrayList<>();
        Map<String, String> pruned = new LinkedHashMap<>();
        Map<PartyAgent, Integer> raised = new HashMap<>();

        for (int round = 1; round <= rounds; round++) {
            List<PartyArgument> fresh = new ArrayList<>();
            for (PartyAgent agent : List.of(prosecutor, defense)) {
                for (Draft draft : agent.argue(facts, graph, round, maxArguments)) {
                    if (draft.isReuse()) {
                        List<Edge> added = agent.reuse(graph, draft.reuse(), draft.targets());
                        if (added.isEmpty()) {
                            continue;
                        }
                        transcript.add(new Turn(round, draft.reuse(), agent.side(), draft.text(), added, true, null));
                        Ruling ruling = judge.decide(graph.scoredSubgraph());
                        events.add(new Linked("link", round, agent.role(), agent.side(), draft.reuse(), added,
                                ruling.strengths(), ruling.claimStrength(), ruling.leading()));
                    } else {
                        int index = raised.merge(agent, 1, Integer::sum) - 1;
                        String id = agent.simulatedArgumentId(index);
                        List<Edge> edges = agent.present(graph, id, draft.text(), draft.targets());
                        turnOf.put(id, transcript.size());
                        transcript.add(new Turn(round, id, agent.side(), draft.text(), edges, false, null));
                        events.add(new Presented("present", round, agent.role(), agent.side(), id, draft.text(),
                                edges));
                        fresh.add(new PartyArgument(id, agent.side(), draft.text()));
                    }
                }
            }

            if (!fresh.isEmpty()) {
                for (ScoredArgument scored : judge.assess(facts, fresh, graph)) {
                    String id = scored.argument().id();
                    String original = RecordedHearing.resolve(scored.duplicateOf(), pruned);
                    Optional<List<Edge>> moved = original == null ? Optional.empty() : graph.merge(id, original);
                    if (moved.isPresent()) {
                        pruned.put(id, original);
                        assessment.add(new ScoredArgument(scored.argument(), scored.category(), scored.strength(),
                                scored.rationale(), original));
                        Turn turn = transcript.get(turnOf.get(id));
                        transcript.set(turnOf.get(id), new Turn(turn.round(), id, turn.side(), turn.text(),
                                turn.edges(), false, original));
                        Ruling ruling = judge.decide(graph.scoredSubgraph());
                        events.add(new Pruned("prune", round, id, original, scored.rationale(), moved.get(),
                                ruling.strengths(), ruling.claimStrength(), ruling.leading()));
                    } else {
                        // כפילות שאי אפשר למזג (צד אחר, טיעון מאוחר יותר, מעגל): הטיעון נשאר ונשקל כרגיל
                        ScoredArgument kept = scored.withoutDuplicate();
                        assessment.add(kept);
                        judge.score(graph, kept, Map.of());
                        Ruling ruling = judge.decide(graph.scoredSubgraph());
                        events.add(new Weighed("weigh", round, id, kept.category().id(), kept.strength(),
                                graph.argument(id).baseScore(), kept.rationale(), ruling.strengths(),
                                ruling.claimStrength(), ruling.leading()));
                    }
                }
            }

            Ruling ruling = judge.decide(graph.scoredSubgraph());
            timeline.add(new RoundState(round, graph.partyArguments().size(), ruling.claimStrength(),
                    ruling.leading()));
        }

        Verdict verdict = RecordedHearing.verdict(judge, graph, legalCase);
        events.add(verdict);
        Ruling ruling = judge.decide(graph);

        GraphReport.Report report = GraphReport.build(legalCase, assessment, graph, ruling);
        SimulationComparison.Comparison comparison =
                SimulationComparison.compare(legalCase, graph, ruling, timeline, recorded);
        return new Result(legalCase.id(), legalCase.title(), rounds, maxArguments, events, transcript, timeline,
                assessment, pruned, ruling.claimStrength(), ruling.strengths(), ruling.leading(),
                legalCase.outcome(), verdict.semantics(), verdict.alternatives(), comparison, report);
    }
}
