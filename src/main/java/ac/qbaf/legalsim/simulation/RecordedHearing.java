package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.PartyAgent;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * מצב א' כדיון שאפשר להציג: הטענות האמיתיות מפסק הדין, לסירוגין, סבב אחרי סבב.
 * בכל סבב סוכן התביעה מכניס טענה של המבקש וסוכן ההגנה טענה של הממשלה.
 * אחרי כל טענה השופט שוקל את הקשתות שבגרף ומכריע מי מוביל, לפי ההערכה השמורה שלו.
 */
public final class RecordedHearing {
    /** סוכן של צד הכניס טיעון לגרף, עם קשת בלי משקל. */
    public record Presented(String type, int round, String agent, String side, String argumentId, String text,
                            String decision) {
    }

    /** השופט שקל את הקשת של הטיעון. scores ו-leading הם ההכרעה על הגרף כפי שהוא עכשיו. */
    public record Weighed(String type, int round, String argumentId, String category, double strength,
                          double weight, String rationale, Map<String, Double> scores, String leading) {
    }

    /** סוף הדיון: ההכרעה של השופט מול פסק הדין האמיתי. */
    public record Verdict(String type, Map<String, Double> scores, String predicted, String actual) {
    }

    public static List<Object> replay(LegalCase legalCase, List<ScoredArgument> assessment) {
        ProsecutorAgent prosecutor = new ProsecutorAgent();
        DefenseAgent defense = new DefenseAgent();
        JudgeAgent judge = new JudgeAgent(message -> {
            throw new IllegalStateException("The replay uses the judge's saved assessment");
        });

        QbafGraph graph = LegalCase.emptyGraph();
        List<String> applicant = prosecutor.submissions(legalCase);
        List<String> government = defense.submissions(legalCase);
        int rounds = Math.max(applicant.size(), government.size());

        List<Object> events = new ArrayList<>();
        Map<String, Object> decision = null;
        for (int i = 0; i < rounds; i++) {
            for (PartyAgent agent : List.of(prosecutor, defense)) {
                List<String> submissions = agent == prosecutor ? applicant : government;
                if (i >= submissions.size()) {
                    continue;
                }
                String id = agent.argumentId(i);
                agent.present(graph, id, submissions.get(i));
                events.add(new Presented("present", i + 1, agent.role(), agent.side(), id, submissions.get(i),
                        agent.decision()));

                judge.weigh(graph, assessment, Map.of());
                decision = judge.decide(graph);
                events.add(weighed(i + 1, id, graph, assessment, decision));
            }
        }
        if (decision != null) {
            events.add(new Verdict("verdict", scores(decision), (String) decision.get("leading"),
                    legalCase.outcome()));
        }
        return events;
    }

    private static Weighed weighed(int round, String id, QbafGraph graph, List<ScoredArgument> assessment,
                                   Map<String, Object> decision) {
        ScoredArgument scored = assessment.stream()
                .filter(s -> s.argument().id().equals(id))
                .findFirst()
                .orElseThrow();
        double weight = graph.supports().stream()
                .filter(s -> s.argument().equals(id))
                .findFirst()
                .orElseThrow()
                .weight();
        return new Weighed("weigh", round, id, graph.argument(id).category().id(), scored.strength(), weight,
                scored.rationale(), scores(decision), (String) decision.get("leading"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> scores(Map<String, Object> decision) {
        return (Map<String, Double>) decision.get("scores");
    }

    private RecordedHearing() {
    }
}
