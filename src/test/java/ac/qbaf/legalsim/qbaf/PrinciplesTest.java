package ac.qbaf.legalsim.qbaf;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * בודק על מאות גרפים אקראיים (חסרי מעגלים, עם טיעונים שעונים לכמה קודקודים) שהסמנטיקות עומדות בעקרונות
 * שהמאמר מוכיח להן: DF-QuAD ו-Euler מאוזנות ומונוטוניות (Propositions 37, 38), QuAD מונוטונית (Proposition 36).
 */
class PrinciplesTest {

    /** גרף אקראי: כל טיעון עונה ל-1–3 קודקודים קודמים, ביחס אקראי, עם ציון בסיס מ-{0, 0.05, ..., 1}. */
    private static QbafGraph randomGraph(Random r) {
        QbafGraph g = LegalCase.emptyGraph();
        List<String> nodes = new ArrayList<>(List.of(QbafGraph.CLAIM));
        int n = 2 + r.nextInt(12);
        for (int i = 1; i <= n; i++) {
            String id = "N" + i;
            Map<String, Relation> targets = new LinkedHashMap<>();
            int count = 1 + r.nextInt(Math.min(3, nodes.size()));
            while (targets.size() < count) {
                targets.put(nodes.get(r.nextInt(nodes.size())), r.nextBoolean() ? Relation.ATTACK : Relation.SUPPORT);
            }
            g.addArgument(id, id, r.nextBoolean() ? "applicant" : "government", targets);
            g.score(id, ArgumentCategory.OTHER, r.nextInt(21) / 20.0);
            nodes.add(id);
        }
        return g;
    }

    @Test
    void dfQuadAndEulerAreBalancedAndMonotonicOnRandomGraphs() {
        Random r = new Random(2019);
        for (int i = 0; i < 400; i++) {
            QbafGraph g = randomGraph(r);
            for (GradualSemantics s : List.of(new DfQuadSemantics(), new EulerSemantics())) {
                Map<String, Double> sigma = s.strengths(g);
                assertEquals(List.of(), Principles.balanceViolations(g, sigma), s.name() + " balance, graph " + i);
                assertEquals(List.of(), Principles.monotonicityViolations(g, sigma),
                        s.name() + " monotonicity, graph " + i);
                sigma.values().forEach(v -> assertTrue(v >= 0.0 && v <= 1.0, s.name() + " range"));
            }
        }
    }

    @Test
    void quadIsMonotonicOnRandomGraphs() {
        Random r = new Random(2015);
        for (int i = 0; i < 400; i++) {
            QbafGraph g = randomGraph(r);
            Map<String, Double> sigma = new QuadSemantics().strengths(g);
            assertEquals(List.of(), Principles.monotonicityViolations(g, sigma), "graph " + i);
            sigma.values().forEach(v -> assertTrue(v >= 0.0 && v <= 1.0));
        }
    }

    @Test
    void setComparisonFollowsDefinition3() {
        // A ≥ B: התאמה חד-חד-ערכית מ-B אל A שבה כל איבר מותאם לאיבר חזק לפחות כמוהו. רשימות ממוינות יורד
        assertTrue(Principles.atLeastAsStrong(List.of(0.9, 0.5), List.of(0.8)));
        assertTrue(Principles.atLeastAsStrong(List.of(0.9, 0.5), List.of(0.9, 0.5)));
        assertFalse(Principles.atLeastAsStrong(List.of(0.9), List.of(0.5, 0.5)));
        assertFalse(Principles.atLeastAsStrong(List.of(0.9, 0.2), List.of(0.5, 0.5)));
        assertTrue(Principles.stronger(List.of(0.9, 0.5), List.of(0.8)));
        assertTrue(Principles.equivalent(List.of(), List.of()));
        // σ/⊥: עוצמה 0 לא נספרת
        assertEquals(List.of(0.7, 0.2), Principles.effective(List.of("a", "b", "c"), Map.of("a", 0.2, "b", 0.0, "c", 0.7)));
    }

    @Test
    void theCheckersCatchABrokenSemantics() {
        // "סמנטיקה" שמתעלמת מהתוקפים והתומכים ומחזירה תמיד 1 − τ: לא מאוזנת ולא מונוטונית
        QbafGraph g = LegalCase.emptyGraph();
        g.addArgument("P1", "p1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        g.score("P1", ArgumentCategory.OTHER, 0.9);
        g.addArgument("P2", "p2", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        g.score("P2", ArgumentCategory.OTHER, 0.2);
        Map<String, Double> broken = Map.of("C", 0.5, "P1", 0.1, "P2", 0.8);

        assertFalse(Principles.balanceViolations(g, broken).isEmpty());
        assertFalse(Principles.monotonicityViolations(g, broken).isEmpty());
    }
}
