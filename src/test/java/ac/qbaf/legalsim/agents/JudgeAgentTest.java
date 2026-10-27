package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.agents.JudgeAgent.Assessment;
import ac.qbaf.legalsim.agents.JudgeAgent.ArgumentScore;
import ac.qbaf.legalsim.agents.JudgeAgent.PartyArgument;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeAgentTest {
    private static final LegalCase CASE = new LegalCase("001-1", "X v. Y", "facts",
            List.of("public figures must accept criticism", "the fine was excessive"),
            List.of("the words were insulting"), true, LegalCase.VIOLATION);

    private static JudgeAgent fakeJudge(List<ArgumentScore> scores) {
        return new JudgeAgent(message -> {
            assertTrue(message.contains("A1 (applicant)"));
            assertTrue(message.contains("G1 (government)"));
            return new Assessment(scores);
        });
    }

    /** הגרף כפי שהסוכנים בונים אותו: התביעה מכניסה את טענות המבקש וההגנה את טענות הממשלה. */
    private static QbafGraph presentedGraph() {
        QbafGraph graph = LegalCase.emptyGraph();
        new ProsecutorAgent().presentSubmissions(graph, CASE);
        new DefenseAgent().presentSubmissions(graph, CASE);
        return graph;
    }

    @Test
    void agentsInsertUnweighedSupportsForTheirOwnSide() {
        QbafGraph graph = presentedGraph();

        assertEquals(LegalCase.VIOLATION, graph.supports().stream()
                .filter(s -> s.argument().equals("A2")).findFirst().orElseThrow().decision());
        assertEquals(LegalCase.NO_VIOLATION, graph.supports().stream()
                .filter(s -> s.argument().equals("G1")).findFirst().orElseThrow().decision());
        graph.supports().forEach(s -> assertNull(s.weight()));
        graph.arguments().forEach(a -> assertNull(a.category()));
    }

    @Test
    void judgeReadsArgumentsFromGraph() {
        List<PartyArgument> arguments = JudgeAgent.argumentsIn(presentedGraph());

        assertEquals(List.of("A1", "A2", "G1"), arguments.stream().map(PartyArgument::id).toList());
        assertEquals(DefenseAgent.SIDE, arguments.get(2).side());
    }

    @Test
    void judgeWeighsEdgesAndDecides() {
        JudgeAgent judge = fakeJudge(List.of(
                new ArgumentScore("A1", "status_of_person", 0.8, "r"),
                new ArgumentScore("A2", "severity_of_sanction", 0.6, "r"),
                new ArgumentScore("G1", "content_and_form", 0.5, "r")));
        QbafGraph graph = presentedGraph();

        List<ScoredArgument> scored = judge.assess(CASE.facts(), JudgeAgent.argumentsIn(graph));
        judge.weigh(graph, scored, Map.of(ArgumentCategory.SEVERITY_OF_SANCTION, 0.5));

        assertEquals(ArgumentCategory.STATUS_OF_PERSON, graph.argument("A1").category());
        Map<String, Object> decision = judge.decide(graph);
        @SuppressWarnings("unchecked")
        Map<String, Double> scores = (Map<String, Double>) decision.get("scores");
        // 0.8 + 0.6 × 0.5 = 1.1 מול 0.5
        assertEquals(1.1, scores.get(LegalCase.VIOLATION), 1e-9);
        assertEquals(0.5, scores.get(LegalCase.NO_VIOLATION), 1e-9);
        assertEquals(LegalCase.VIOLATION, decision.get("leading"));
    }

    @Test
    void rejectsMissingScoreOrUnknownCategory() {
        List<PartyArgument> arguments = JudgeAgent.argumentsIn(presentedGraph());

        assertThrows(IllegalStateException.class, () -> fakeJudge(List.of(
                new ArgumentScore("A1", "other", 0.5, "r"))).assess(CASE.facts(), arguments));
        assertThrows(IllegalArgumentException.class, () -> fakeJudge(List.of(
                new ArgumentScore("A1", "made_up", 0.5, "r"),
                new ArgumentScore("A2", "other", 0.5, "r"),
                new ArgumentScore("G1", "other", 0.5, "r"))).assess(CASE.facts(), arguments));
    }
}
