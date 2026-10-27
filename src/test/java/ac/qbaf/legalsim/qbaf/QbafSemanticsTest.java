package ac.qbaf.legalsim.qbaf;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QbafSemanticsTest {
    private static final double DELTA = 1e-9;

    private final QbafSemantics semantics = new QbafSemantics();

    private static QbafGraph criminalCase() {
        QbafGraph graph = new QbafGraph();
        graph.addDecision("conviction", "הרשעה");
        graph.addDecision("acquittal", "זיכוי");
        return graph;
    }

    /** טיעון שסוכן הכניס, ואחריו המשקל שהשופט קבע. */
    private static void addWeighed(QbafGraph graph, String id, String decision, double weight) {
        graph.addArgument(id, id);
        graph.addSupport(id, decision);
        graph.weigh(id, ArgumentCategory.OTHER, weight);
    }

    @Test
    void decisionWithoutSupportScoresZero() {
        Map<String, Double> scores = semantics.score(criminalCase());

        assertEquals(0.0, scores.get("conviction"), DELTA);
        assertEquals(0.0, scores.get("acquittal"), DELTA);
    }

    @Test
    void supportsAggregateAsWeightedSum() {
        QbafGraph graph = criminalCase();
        addWeighed(graph, "dna", "conviction", 0.5);
        addWeighed(graph, "witness", "conviction", 0.3);

        // 0.5 + 0.3 = 0.8
        assertEquals(0.8, semantics.score(graph).get("conviction"), DELTA);
    }

    @Test
    void leadingDecisionIsStrongestDecision() {
        QbafGraph graph = criminalCase();
        assertEquals(QbafSemantics.UNDECIDED, semantics.leadingDecision(graph));

        addWeighed(graph, "dna", "conviction", 0.6);
        assertEquals("conviction", semantics.leadingDecision(graph));

        addWeighed(graph, "alibi", "acquittal", 0.6);
        assertEquals(QbafSemantics.UNDECIDED, semantics.leadingDecision(graph));

        addWeighed(graph, "witness", "acquittal", 0.3);
        assertEquals("acquittal", semantics.leadingDecision(graph));
    }

    @Test
    void graphRejectsInvalidInput() {
        QbafGraph graph = criminalCase();
        graph.addArgument("a", "טענה");

        assertThrows(IllegalArgumentException.class, () -> graph.addArgument("a", "כפול"));
        assertThrows(IllegalArgumentException.class, () -> graph.addArgument("conviction", "מזהה תפוס"));
        assertThrows(IllegalArgumentException.class, () -> graph.addSupport("a", "missing"));
        assertThrows(IllegalArgumentException.class, () -> graph.addSupport("missing", "conviction"));
        assertThrows(IllegalArgumentException.class, () -> graph.weigh("a", ArgumentCategory.OTHER, 0.5));

        graph.addSupport("a", "conviction");
        assertThrows(IllegalArgumentException.class, () -> graph.addSupport("a", "acquittal"));
        assertThrows(IllegalArgumentException.class, () -> graph.weigh("a", ArgumentCategory.OTHER, 1.5));
    }

    @Test
    void cannotDecideBeforeJudgeWeighsEveryEdge() {
        QbafGraph graph = criminalCase();
        graph.addArgument("a", "טענה");
        graph.addSupport("a", "conviction");

        assertThrows(IllegalStateException.class, () -> semantics.score(graph));

        graph.weigh("a", ArgumentCategory.OTHER, 0.4);
        assertEquals(0.4, semantics.score(graph).get("conviction"), DELTA);
    }
}
