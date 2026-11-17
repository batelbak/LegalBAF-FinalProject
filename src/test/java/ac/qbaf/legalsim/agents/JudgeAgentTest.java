package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.agents.JudgeAgent.ArgumentScore;
import ac.qbaf.legalsim.agents.JudgeAgent.Assessment;
import ac.qbaf.legalsim.agents.JudgeAgent.PartyArgument;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.PartyAgent.Draft;
import ac.qbaf.legalsim.agents.PartyAgent.Link;
import ac.qbaf.legalsim.agents.PartyAgent.Links;
import ac.qbaf.legalsim.agents.PartyAgent.Move;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafGraph.Edge;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Ruling;
import ac.qbaf.legalsim.simulation.RecordedHearing;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
        return new JudgeAgent(message -> new Assessment(scores));
    }

    /** הגרף כפי שהסוכנים בונים אותו: A1 ו-A2 תומכים ב-C, G1 תוקף את A1. */
    private static QbafGraph presentedGraph() {
        QbafGraph graph = LegalCase.emptyGraph();
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null);
        DefenseAgent defense = new DefenseAgent(m -> null);
        prosecutor.present(graph, "A1", "public figures must accept criticism", QbafGraph.CLAIM);
        defense.present(graph, "G1", "the words were insulting", "A1");
        prosecutor.present(graph, "A2", "the fine was excessive", QbafGraph.CLAIM);
        return graph;
    }

    @Test
    void relationsFollowFromTheSides() {
        QbafGraph graph = presentedGraph();
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null);
        DefenseAgent defense = new DefenseAgent(m -> null);

        assertEquals(Relation.SUPPORT, graph.edgesFrom("A1").get(0).relation());
        assertEquals(Relation.ATTACK, graph.edgesFrom("G1").get(0).relation());
        assertEquals(Relation.ATTACK, defense.relationTo(graph, QbafGraph.CLAIM));
        assertEquals(Relation.ATTACK, prosecutor.relationTo(graph, "G1"));
        assertEquals(Relation.SUPPORT, prosecutor.relationTo(graph, "A2"));
        assertEquals(Relation.SUPPORT, defense.relationTo(graph, "G1"));
        graph.partyArguments().forEach(a -> assertNull(a.baseScore()));
    }

    @Test
    void oneArgumentAnswersSeveralNodesWithTheRightRelationToEach() {
        QbafGraph graph = presentedGraph();
        DefenseAgent defense = new DefenseAgent(m -> null);

        // טיעון ממשלתי אחד: תוקף את C ואת A2, ומחזק את G1
        List<Edge> edges = defense.present(graph, "G2", "one point, three targets", List.of("C", "A2", "G1"));

        assertEquals(List.of(new Edge("G2", "C", Relation.ATTACK), new Edge("G2", "A2", Relation.ATTACK),
                new Edge("G2", "G1", Relation.SUPPORT)), edges);
        assertTrue(PartyAgent.describe(graph).contains("G2 (government) attacks C, attacks A2, supports G1: "));
    }

    @Test
    void reuseAddsEdgesFromAnExistingArgumentAndSkipsInvalidOnes() {
        QbafGraph graph = presentedGraph();
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null);
        DefenseAgent defense = new DefenseAgent(m -> null);
        defense.present(graph, "G2", "the fine was modest", "A2");

        // A1 מכוון גם נגד G2. נגד G1 אי אפשר (G1 תוקף את A1: מעגל), ואל C כבר יש קשת
        List<Edge> added = prosecutor.reuse(graph, "A1", List.of("G1", "C", "G2"));

        assertEquals(List.of(new Edge("A1", "G2", Relation.ATTACK)), added);
        assertEquals(2, graph.edgesFrom("A1").size());
        assertThrows(IllegalArgumentException.class, () -> prosecutor.reuse(graph, "G1", List.of("C")));
    }

    @Test
    void argueKeepsOnlyValidItems() {
        QbafGraph graph = presentedGraph();
        new DefenseAgent(m -> null).present(graph, "G2", "the fine was modest", "A2");
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> new Move(Arrays.asList(
                new Draft("new point", List.of("G2", "G2", "X9")),          // יעד כפול ויעד לא קיים מסוננים
                new Draft("no valid target", List.of("X9")),                 // בלי יעד תקין: C
                new Draft("", List.of("C")),                                 // בלי טקסט: נזרק
                new Draft("ignored", List.of("G2", "G1"), "A1"),             // שימוש חוזר: G1 סוגר מעגל, G2 תקין
                new Draft("not mine", List.of("C"), "G1"),                   // טיעון של הצד השני: נחשב טיעון חדש
                null,
                new Draft("pointless reuse", List.of("C"), "A1"))));         // כבר קיימת קשת: נזרק

        List<Draft> drafts = prosecutor.argue("facts", graph, 2, 10);

        assertEquals(4, drafts.size());
        assertEquals(new Draft("new point", List.of("G2"), null), drafts.get(0));
        assertEquals(List.of("C"), drafts.get(1).targets());
        assertEquals(new Draft("public figures must accept criticism", List.of("G2"), "A1"), drafts.get(2));
        assertTrue(drafts.get(2).isReuse());
        assertEquals(new Draft("not mine", List.of("C"), null), drafts.get(3));
        assertEquals(2, prosecutor.argue("facts", graph, 2, 2).size());
    }

    @Test
    void judgeSeesWhatEachArgumentAnswers() {
        String[] seen = new String[1];
        JudgeAgent judge = new JudgeAgent(message -> {
            seen[0] = message;
            return new Assessment(List.of(new ArgumentScore("G1", "content_and_form", 0.5, "r")));
        });
        QbafGraph graph = presentedGraph();

        judge.assess(CASE.facts(), List.of(new PartyArgument("G1", "government", "the words were insulting")), graph);

        assertTrue(seen[0].contains("G1 (government) attacks A1: the words were insulting"));
        assertTrue(seen[0].contains("A1 (applicant) supports C:"));
        assertTrue(seen[0].contains("C (central claim):"));
    }

    @Test
    void judgeSetsBaseScoresAndTheSemanticsDecides() {
        JudgeAgent judge = fakeJudge(List.of(
                new ArgumentScore("A1", "status_of_person", 0.8, "r"),
                new ArgumentScore("A2", "severity_of_sanction", 0.6, "r"),
                new ArgumentScore("G1", "content_and_form", 0.5, "r")));
        QbafGraph graph = presentedGraph();

        List<ScoredArgument> scored = judge.assess(CASE.facts(), JudgeAgent.argumentsIn(graph), graph);
        judge.weigh(graph, scored, Map.of(ArgumentCategory.SEVERITY_OF_SANCTION, 0.5));
        Ruling ruling = judge.decide(graph);

        assertEquals(ArgumentCategory.STATUS_OF_PERSON, graph.argument("A1").category());
        assertEquals(0.3, graph.argument("A2").baseScore(), 1e-9);
        // A1: 0.8 מותקף ע"י G1 (0.5) → 0.4. תומכי C: 0.4 ו-0.3 → 0.58. σ(C) = 0.5 + 0.5·0.58 = 0.79
        assertEquals(0.4, ruling.strengths().get("A1"), 1e-9);
        assertEquals(0.79, ruling.claimStrength(), 1e-9);
        assertEquals(LegalCase.VIOLATION, ruling.leading());
        assertEquals("DF-QuAD", judge.semanticsName());
    }

    @Test
    void judgeCanFlagADuplicate() {
        JudgeAgent judge = fakeJudge(List.of(
                new ArgumentScore("A1", "other", 0.5, "", "r"),
                new ArgumentScore("A2", "other", 0.5, " A1 ", "restates A1"),
                new ArgumentScore("G1", "other", 0.5, "none", "r")));

        List<ScoredArgument> scored = judge.assess(CASE.facts(), JudgeAgent.argumentsIn(presentedGraph()));

        assertNull(scored.get(0).duplicateOf());
        assertNull(scored.get(1).duplicateOf(), "G1 said 'none'");
        assertEquals("A1", scored.get(2).duplicateOf());
        assertNull(scored.get(2).withoutDuplicate().duplicateOf());
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

    @Test
    void submissionsMayAnswerAnyOtherSubmission() {
        String[] seen = new String[1];
        List<PartyAgent.Submission> order = List.of(
                new PartyAgent.Submission("A1", "applicant", "a1"), new PartyAgent.Submission("G1", "government", "g1"),
                new PartyAgent.Submission("A2", "applicant", "a2"), new PartyAgent.Submission("G2", "government", "g2"));
        // G1 עונה ל-A2, שבא אחריו (חוקי), ומסומן גם אל C — C מושמט; A1 לא של ההגנה, ולכן מתעלמים
        DefenseAgent defense = new DefenseAgent(m -> null, message -> {
            seen[0] = message;
            return new Links(List.of(new Link("G1", List.of("A2", "C")), new Link("G2", List.of("C")),
                    new Link("A1", List.of("G1"))));
        });
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null,
                message -> new Links(List.of(new Link("A1", List.of("C")), new Link("A2", List.of("G1", "A1", "A2")))));

        Map<String, List<String>> defenseLinks = defense.linkSubmissions(CASE.facts(), order);
        Map<String, List<String>> prosecutionLinks = prosecutor.linkSubmissions(CASE.facts(), order);

        assertEquals(Map.of("G1", List.of("A2"), "G2", List.of("C")), defenseLinks);
        // A2 עונה ל-G1 ול-A1, אבל לא לעצמו
        assertEquals(Map.of("A1", List.of("C"), "A2", List.of("G1", "A1")), prosecutionLinks);
        assertTrue(seen[0].contains("G1: C, A1, A2, G2"));

        // A1 ו-G1 עונים זה לזה: לפי הסדר הבסיסי A1 → G1 נשאר, G1 → A1 היה סוגר מעגל ולכן G1 עובר ל-C
        Map<String, List<String>> structure = Map.of("A1", List.of("G1"), "G1", List.of("A1"),
                "A2", List.of("G1", "A1"), "G2", List.of("C"));
        Map<String, List<String>> acyclic = RecordedHearing.acyclic(order, structure);
        assertEquals(List.of("G1"), acyclic.get("A1"));
        assertEquals(List.of("C"), acyclic.get("G1"));
        assertEquals(List.of("G1", "A1"), acyclic.get("A2"));
    }

    @Test
    void aLinkToEveryEarlierNodeIsAskedAgainThenFallsBackToTheClaim() {
        // המודל מעתיק את כל הרשימה המותרת ל-G2 (C, A1, G1, A2): נשאל שוב, ובפעם השנייה עונה נכון
        List<String> messages = new java.util.ArrayList<>();
        DefenseAgent defense = new DefenseAgent(m -> null, message -> {
            messages.add(message);
            return messages.size() == 1
                    ? new Links(List.of(new Link("G1", List.of("C")), new Link("G2", List.of("C", "A1", "G1", "A2"))))
                    : new Links(List.of(new Link("G2", List.of("A2"))));
        });
        List<PartyAgent.Submission> order = List.of(
                new PartyAgent.Submission("A1", "applicant", "a1"), new PartyAgent.Submission("G1", "government", "g1"),
                new PartyAgent.Submission("A2", "applicant", "a2"), new PartyAgent.Submission("G2", "government", "g2"));

        Map<String, List<String>> links = defense.linkSubmissions(CASE.facts(), order);

        assertEquals(List.of("C"), links.get("G1"));
        assertEquals(List.of("A2"), links.get("G2"));
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("Your previous answer gave G2 more than"));

        // אם גם התשובה השנייה מעתיקה את הרשימה, הטענה נשארת נקודה עיקרית (C)
        DefenseAgent stubborn = new DefenseAgent(m -> null,
                message -> new Links(List.of(new Link("G2", List.of("A1", "G1", "A2")))));
        assertEquals(List.of("C"), stubborn.linkSubmissions(CASE.facts(), order).get("G2"));
    }

    @Test
    void aSideWithNoMainPointIsAskedAgainThenKeepsItsFirstSubmissionAtTheClaim() {
        List<PartyAgent.Submission> order = List.of(
                new PartyAgent.Submission("A1", "applicant", "a1"), new PartyAgent.Submission("G1", "government", "g1"),
                new PartyAgent.Submission("A2", "applicant", "a2"), new PartyAgent.Submission("G2", "government", "g2"));
        // בפעם הראשונה כל טענות המבקש הן תגובות; בשנייה A2 הוא נקודה עיקרית
        List<String> messages = new java.util.ArrayList<>();
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null, message -> {
            messages.add(message);
            return messages.size() == 1
                    ? new Links(List.of(new Link("A1", List.of("G1")), new Link("A2", List.of("G2"))))
                    : new Links(List.of(new Link("A1", List.of("G1")), new Link("A2", List.of("C"))));
        });

        assertEquals(Map.of("A1", List.of("G1"), "A2", List.of("C")), prosecutor.linkSubmissions(CASE.facts(), order));
        assertTrue(messages.get(1).contains("Your answer has no main point"));

        // אם גם בפעם השנייה אין נקודה עיקרית, הטענה הראשונה של הצד עוברת ל-C
        ProsecutorAgent stubborn = new ProsecutorAgent(m -> null,
                message -> new Links(List.of(new Link("A1", List.of("G1")), new Link("A2", List.of("G2")))));
        assertEquals(Map.of("A1", List.of("C"), "A2", List.of("G2")), stubborn.linkSubmissions(CASE.facts(), order));
    }
}
