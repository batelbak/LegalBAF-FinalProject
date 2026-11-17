package ac.qbaf.legalsim.qbaf;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Ruling;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * הסמנטיקות מול הדוגמאות שבמאמר (docs/Baroni-Rago-Toni-2019-...pdf) ומול עקרונות Balance ו-Monotonicity.
 */
class QbafSemanticsTest {
    private static final double DELTA = 1e-9;

    private static QbafGraph graph() {
        return LegalCase.emptyGraph();
    }

    private static void add(QbafGraph g, String id, String side, String target, Relation relation, double tau) {
        g.addArgument(id, id, side, target, relation);
        g.score(id, ArgumentCategory.OTHER, tau);
    }

    /** איור 7 במאמר: R⁻(a) = {b, c}, R⁺(a) = {d, e}. כאן a היא הטענה המרכזית C (τ = 0.5). */
    private static QbafGraph figure7(double b, double c, double d, double e) {
        QbafGraph g = graph();
        add(g, "b", "government", QbafGraph.CLAIM, Relation.ATTACK, b);
        add(g, "c", "government", QbafGraph.CLAIM, Relation.ATTACK, c);
        add(g, "d", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, d);
        add(g, "e", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, e);
        return g;
    }

    @Test
    void dfQuadReproducesThePaperExample() {
        // "the DF-QuAD algorithm gives σ(a) = 0.425" עבור τ(b)=1, τ(c)=0.2, τ(d)=0.7, τ(e)=0.5
        Ruling ruling = new QbafSemantics().rule(figure7(1.0, 0.2, 0.7, 0.5));

        assertEquals(0.425, ruling.claimStrength(), DELTA);
        assertEquals(LegalCase.NO_VIOLATION, ruling.leading());
    }

    @Test
    void eulerReproducesThePaperExample() {
        // "σ(a) = 0.425 < τ(a)" עבור τ(b)=0.1, τ(c)=0.9, τ(d)=0.2, τ(e)=0.3 (מעוגל במאמר)
        Ruling ruling = new QbafSemantics(new EulerSemantics()).rule(figure7(0.1, 0.9, 0.2, 0.3));

        assertEquals(0.425, ruling.claimStrength(), 0.001);
        assertEquals(LegalCase.NO_VIOLATION, ruling.leading());
    }

    @Test
    void quadReproducesThePaperExamples() {
        QbafSemantics quad = new QbafSemantics(new QuadSemantics());

        // QuAD (2015), הדוגמה הרצה: BS(A1)=0.5, C1=0.7 תוקף, P1=0.9 תומך → SF(A1) = g(0.5, 0.15, 0.95) = 0.55
        QbafGraph a1 = graph();
        add(a1, "C1", "government", QbafGraph.CLAIM, Relation.ATTACK, 0.7);
        add(a1, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 0.9);
        assertEquals(0.55, quad.rule(a1).claimStrength(), DELTA);

        // BS(A2)=0.5 עם תוקף יחיד C2=0.4 ובלי תומכים → SF(A2) = 0.3
        QbafGraph a2 = graph();
        add(a2, "C2", "government", QbafGraph.CLAIM, Relation.ATTACK, 0.4);
        assertEquals(0.3, quad.rule(a2).claimStrength(), DELTA);

        // QuAD סעיף 5: a מותקף ע"י b; b מותקף ע"י c ונתמך ע"י d; כל ציוני הבסיס 1 → SF(b) = SF(a) = 0.5
        QbafGraph baf = graph();
        add(baf, "a", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 1.0);
        add(baf, "b", "government", "a", Relation.ATTACK, 1.0);
        add(baf, "c", "applicant", "b", Relation.ATTACK, 1.0);
        add(baf, "d", "government", "b", Relation.SUPPORT, 1.0);
        assertEquals(0.5, quad.rule(baf).strengths().get("b"), DELTA);
        assertEquals(0.5, quad.rule(baf).strengths().get("a"), DELTA);
    }

    @Test
    void quadIsNotBalancedButDfQuadIs() {
        // IJAR 2019 אחרי Proposition 36: R⁻(a) = {b, c}, R⁺(a) = {d, e}, כל ציוני הבסיס 1.
        // QuAD נותנת σ(a) = 0.5 אף שהתוקפים והתומכים שקולים; DF-QuAD משאירה σ(a) = τ(a) = 1
        QbafGraph g = graph();
        add(g, "a", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 1.0);
        add(g, "b", "government", "a", Relation.ATTACK, 1.0);
        add(g, "c", "government", "a", Relation.ATTACK, 1.0);
        add(g, "d", "applicant", "a", Relation.SUPPORT, 1.0);
        add(g, "e", "applicant", "a", Relation.SUPPORT, 1.0);

        java.util.Map<String, Double> quad = new QuadSemantics().strengths(g);
        java.util.Map<String, Double> dfQuad = new DfQuadSemantics().strengths(g);

        assertEquals(0.5, quad.get("a"), DELTA);
        assertEquals(1.0, dfQuad.get("a"), DELTA);
        assertTrue(Principles.balanceViolations(g, quad).stream().anyMatch(v -> v.startsWith("a:")));
        assertTrue(Principles.balanceViolations(g, dfQuad).isEmpty());
    }

    @Test
    void zeroStrengthArgumentsHaveNoEffectAndFullStrengthSaturates() {
        // QuAD סעיף 4.1: תוקף או תומך בעוצמה 0 "אפשר להתעלם ממנו"; תוקף בעוצמה 1 מרווה את ההתקפה
        for (GradualSemantics s : QbafSemantics.all()) {
            QbafGraph g = graph();
            add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 0.6);
            double before = s.strengths(g).get(QbafGraph.CLAIM);
            add(g, "D1", "government", QbafGraph.CLAIM, Relation.ATTACK, 0.0);
            assertEquals(before, s.strengths(g).get(QbafGraph.CLAIM), DELTA, s.name());
        }
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 0.6);
        add(g, "D1", "government", "P1", Relation.ATTACK, 1.0);
        add(g, "D2", "government", "P1", Relation.ATTACK, 0.3);
        assertEquals(0.0, new DfQuadSemantics().strengths(g).get("P1"), DELTA);
    }

    @Test
    void semanticsIsSelectableAndAlternativesCoverAllThree() {
        assertEquals("DF-QuAD", QbafSemantics.byId(null).name());
        assertEquals("DF-QuAD", QbafSemantics.byId("nonsense").name());
        assertEquals("QuAD", QbafSemantics.byId("quad").name());
        assertEquals("Euler-based", QbafSemantics.byId(" Euler ").name());
        assertEquals("DF-QuAD", QbafSemantics.byId("DF_QUAD").name());

        java.util.Map<String, QbafSemantics.Alternative> alternatives =
                QbafSemantics.alternatives(figure7(1.0, 0.2, 0.7, 0.5));
        assertEquals(java.util.List.of("df-quad", "quad", "euler"), java.util.List.copyOf(alternatives.keySet()));
        assertEquals(0.425, alternatives.get("df-quad").claimStrength(), DELTA);
        assertEquals(LegalCase.NO_VIOLATION, alternatives.get("df-quad").leading());
    }

    @Test
    void unchallengedArgumentKeepsItsBaseScoreAndEmptyGraphIsUndecided() {
        // GP1 / Stability: בלי תוקפים ותומכים σ = τ
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 0.6);
        Ruling ruling = new QbafSemantics().rule(g);

        assertEquals(0.6, ruling.strengths().get("P1"), DELTA);
        assertEquals(0.8, ruling.claimStrength(), DELTA);
        assertEquals(LegalCase.VIOLATION, ruling.leading());
        assertEquals(QbafSemantics.UNDECIDED, new QbafSemantics().rule(graph()).leading());
    }

    @Test
    void balancedAttackAndSupportLeaveTheClaimNeutral() {
        // Balance: תוקפים ותומכים שקולים → σ = τ, ולכן undecided
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 0.7);
        add(g, "D1", "government", QbafGraph.CLAIM, Relation.ATTACK, 0.7);

        for (GradualSemantics s : new GradualSemantics[]{new DfQuadSemantics(), new EulerSemantics()}) {
            Ruling ruling = new QbafSemantics(s).rule(g);
            assertEquals(0.5, ruling.claimStrength(), DELTA, s.name());
            assertEquals(QbafSemantics.UNDECIDED, ruling.leading(), s.name());
        }
    }

    @Test
    void aRebuttalWeakensTheArgumentItAttacksAndSoItsEffectOnTheClaim() {
        QbafGraph g = graph();
        add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 0.8);
        add(g, "D1", "government", QbafGraph.CLAIM, Relation.ATTACK, 0.6);
        double before = new QbafSemantics().rule(g).claimStrength();

        // D2 תוקף את P1: P1 נחלש (0.8 − 0.8·0.9 = 0.08), ולכן C נחלשת
        add(g, "D2", "government", "P1", Relation.ATTACK, 0.9);
        Ruling after = new QbafSemantics().rule(g);

        assertEquals(0.08, after.strengths().get("P1"), DELTA);
        assertTrue(after.claimStrength() < before);
        assertEquals(LegalCase.NO_VIOLATION, after.leading());
        // וחיזוק של P1 מחזיר חלק מהכוח: A הוא תומך של P1
        add(g, "P2", "applicant", "P1", Relation.SUPPORT, 0.9);
        assertTrue(new QbafSemantics().rule(g).strengths().get("P1") > 0.08);
    }

    @Test
    void strongerAttackerNeverRaisesStrength() {
        // Monotonicity
        double previous = Double.MAX_VALUE;
        for (double tau = 0.0; tau <= 1.0; tau += 0.25) {
            QbafGraph g = graph();
            add(g, "P1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT, 0.6);
            add(g, "D1", "government", QbafGraph.CLAIM, Relation.ATTACK, tau);
            double sigma = new QbafSemantics().rule(g).claimStrength();
            assertTrue(sigma <= previous + DELTA);
            previous = sigma;
        }
    }

    @Test
    void cannotDecideBeforeEveryArgumentIsScored() {
        QbafGraph g = graph();
        g.addArgument("P1", "p1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);

        assertThrows(IllegalStateException.class, () -> new QbafSemantics().rule(g));
    }

    @Test
    void graphIsATreeOfEarlierNodes() {
        QbafGraph g = graph();
        g.addArgument("P1", "p1", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        g.addArgument("D1", "d1", "government", "P1", Relation.ATTACK);
        g.addArgument("P2", "p2", "applicant", "D1", Relation.ATTACK);

        assertEquals(3, g.depth("P2"));
        assertEquals(java.util.List.of("D1"), g.attackers("P1"));
        assertEquals(java.util.List.of("P1"), g.supporters(QbafGraph.CLAIM));
        // היעד חייב להיות קיים כבר, ולכן אין מעגלים
        assertThrows(IllegalArgumentException.class,
                () -> g.addArgument("D2", "d2", "government", "P9", Relation.ATTACK));
        assertThrows(IllegalArgumentException.class,
                () -> g.addArgument("P1", "again", "applicant", QbafGraph.CLAIM, Relation.SUPPORT));
        assertThrows(IllegalArgumentException.class, () -> g.score(QbafGraph.CLAIM, ArgumentCategory.OTHER, 0.9));
        assertThrows(IllegalArgumentException.class, () -> g.score("P1", ArgumentCategory.OTHER, 1.2));
    }
}
