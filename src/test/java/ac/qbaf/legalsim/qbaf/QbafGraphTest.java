package ac.qbaf.legalsim.qbaf;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph.Edge;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** המבנה של הגרף: יחס חסר מעגלים כללי (לא רק עץ), מיזוג כפילויות, ותת-הגרף של הטיעונים שנשקלו. */
class QbafGraphTest {
    private static final double DELTA = 1e-9;

    private static QbafGraph graph() {
        return LegalCase.emptyGraph();
    }

    private static void add(QbafGraph g, String id, String side, String target, Relation relation) {
        g.addArgument(id, id, side, target, relation);
    }

    private static void scoreAll(QbafGraph g, double tau) {
        g.partyArguments().forEach(a -> g.score(a.id(), ArgumentCategory.OTHER, tau));
    }

    @Test
    void oneArgumentCanAnswerSeveralNodes() {
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        add(g, "D1", "government", QbafGraph.CLAIM, Relation.ATTACK);
        Map<String, Relation> targets = new LinkedHashMap<>();
        targets.put("D1", Relation.ATTACK);
        targets.put(QbafGraph.CLAIM, Relation.SUPPORT);
        g.addArgument("P2", "p2", "applicant", targets);
        g.score("P1", ArgumentCategory.OTHER, 0.6);
        g.score("D1", ArgumentCategory.OTHER, 0.6);
        g.score("P2", ArgumentCategory.OTHER, 0.5);

        assertEquals(List.of(new Edge("P2", "D1", Relation.ATTACK), new Edge("P2", "C", Relation.SUPPORT)),
                g.edgesFrom("P2"));
        assertEquals(List.of("P2"), g.attackers("D1"));
        assertEquals(List.of("P1", "P2"), g.supporters(QbafGraph.CLAIM));
        // המרחק מ-C הוא לפי המסלול הקצר
        assertEquals(1, g.depth("P2"));

        // D1 = 0.6 − 0.6·0.5 = 0.3; תומכי C: 1 − 0.4·0.5 = 0.8; תוקף 0.3 → σ(C) = 0.5 + 0.5·0.5 = 0.75
        Map<String, Double> sigma = new DfQuadSemantics().strengths(g);
        assertEquals(0.3, sigma.get("D1"), DELTA);
        assertEquals(0.75, sigma.get(QbafGraph.CLAIM), DELTA);
    }

    @Test
    void sharedNodesFromThePaperAreEvaluatedOnceAndUsedEverywhere() {
        // QuAD (2015) איור 3: R = {(P1, A1), (P1, A2), (C1, A2), (C1, P1)}.
        // כאן A1 ו-A2 הן שתי טענות משנה מתחת ל-C, וכל ציוני הבסיס 0.5
        QbafGraph g = graph();
        add(g, "A1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        add(g, "A2", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        g.addArgument("P1", "p1", "applicant", Map.of("A1", Relation.SUPPORT));
        g.addEdge("P1", "A2", Relation.SUPPORT);
        g.addArgument("C1", "c1", "government", Map.of("A2", Relation.ATTACK));
        g.addEdge("C1", "P1", Relation.ATTACK);
        scoreAll(g, 0.5);

        Map<String, Double> sigma = new DfQuadSemantics().strengths(g);
        assertEquals(0.25, sigma.get("P1"), DELTA);    // מותקף ע"י C1 (0.5)
        assertEquals(0.625, sigma.get("A1"), DELTA);   // נתמך ע"י P1 (0.25)
        assertEquals(0.375, sigma.get("A2"), DELTA);   // נתמך ע"י P1 (0.25), מותקף ע"י C1 (0.5)
    }

    @Test
    void edgesThatWouldBreakTheFrameworkAreRejected() {
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        add(g, "D1", "government", "P1", Relation.ATTACK);

        assertFalse(g.canAddEdge("P1", "D1"), "D1 already depends on P1: a cycle");
        assertFalse(g.canAddEdge("P1", "P1"));
        assertFalse(g.canAddEdge(QbafGraph.CLAIM, "P1"), "the claim answers nothing");
        assertFalse(g.canAddEdge("P1", "X9"));
        assertTrue(g.canAddEdge("D1", QbafGraph.CLAIM));
        g.addEdge("D1", QbafGraph.CLAIM, Relation.ATTACK);
        assertFalse(g.canAddEdge("D1", QbafGraph.CLAIM), "at most one edge between two nodes");
        assertThrows(IllegalArgumentException.class, () -> g.addEdge("P1", "D1", Relation.ATTACK));
        assertThrows(IllegalArgumentException.class, () -> g.addArgument("P2", "p2", "applicant", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> g.addArgument("P2", "p2", "applicant", Map.of("X9", Relation.ATTACK)));
        assertTrue(g.reaches("D1", QbafGraph.CLAIM));
        assertFalse(g.reaches(QbafGraph.CLAIM, "D1"));
    }

    @Test
    void mergingADuplicateMovesItsEdgesToTheOriginal() {
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        add(g, "D1", "government", QbafGraph.CLAIM, Relation.ATTACK);
        // P2 חוזר על P1, אבל מכוון גם נגד D1; D2 תוקף את P2
        Map<String, Relation> targets = new LinkedHashMap<>();
        targets.put(QbafGraph.CLAIM, Relation.SUPPORT);
        targets.put("D1", Relation.ATTACK);
        g.addArgument("P2", "p2", "applicant", targets);
        add(g, "D2", "government", "P2", Relation.ATTACK);

        Optional<List<Edge>> moved = g.merge("P2", "P1");

        assertEquals(List.of(new Edge("D2", "P1", Relation.ATTACK), new Edge("P1", "D1", Relation.ATTACK)),
                moved.orElseThrow());
        assertFalse(g.contains("P2"));
        // הקשת P2→C לא שוכפלה: ל-P1 כבר הייתה קשת אל C
        assertEquals(List.of(new Edge("P1", "C", Relation.SUPPORT), new Edge("P1", "D1", Relation.ATTACK)),
                g.edgesFrom("P1"));
        assertEquals(List.of(new Edge("D2", "P1", Relation.ATTACK)), g.edgesFrom("D2"));
    }

    @Test
    void mergeDropsEdgesThatWouldCloseACycle() {
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        add(g, "D1", "government", "P1", Relation.ATTACK);
        // P2 חוזר על P1 כתשובה ל-D1. P1 לא יכול לתקוף את מי שתוקף אותו, ולכן הקשת מושמטת
        add(g, "P2", "applicant", "D1", Relation.ATTACK);

        assertEquals(List.of(), g.merge("P2", "P1").orElseThrow());
        assertFalse(g.contains("P2"));
        assertEquals(List.of(), g.attackers("D1"));
    }

    @Test
    void mergeIsRefusedWhenItIsNotAValidDuplicate() {
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        add(g, "D1", "government", QbafGraph.CLAIM, Relation.ATTACK);
        add(g, "P2", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);

        assertTrue(g.merge("P2", "D1").isEmpty(), "different sides");
        assertTrue(g.merge("P1", "P2").isEmpty(), "the original must be the earlier argument");
        assertTrue(g.merge("P2", "P2").isEmpty());
        assertTrue(g.merge("P2", "X9").isEmpty());
        assertTrue(g.merge("P2", QbafGraph.CLAIM).isEmpty());
        assertTrue(g.contains("P2"));

        // D2 עונה רק ל-P2, ו-P1 כבר תוקף את D2: אחרי מיזוג D2 היה נשאר בלי קשת (D2→P1 סוגר מעגל). נדחה
        add(g, "D2", "government", "P2", Relation.ATTACK);
        g.addEdge("P1", "D2", Relation.ATTACK);
        assertTrue(g.merge("P2", "P1").isEmpty());
        assertTrue(g.contains("P2"));
        assertEquals(List.of(new Edge("D2", "P2", Relation.ATTACK)), g.edgesFrom("D2"));
    }

    @Test
    void scoredSubgraphHoldsOnlyWhatTheJudgeHasWeighed() {
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        add(g, "D1", "government", "P1", Relation.ATTACK);
        add(g, "P2", "applicant", "D1", Relation.ATTACK);
        g.score("P1", ArgumentCategory.OTHER, 0.6);
        g.score("P2", ArgumentCategory.OTHER, 0.9);

        QbafGraph scored = g.scoredSubgraph();

        assertEquals(List.of("C", "P1", "P2"), scored.arguments().stream().map(QbafGraph.Argument::id).toList());
        assertEquals(List.of(new Edge("P1", "C", Relation.SUPPORT)), scored.edges());
        // D1 עוד לא נשקל, ולכן לא משפיע: σ(P1) = τ(P1)
        assertEquals(0.6, new DfQuadSemantics().strengths(scored).get("P1"), DELTA);
        assertThrows(IllegalStateException.class, () -> new DfQuadSemantics().strengths(g));
    }
}
