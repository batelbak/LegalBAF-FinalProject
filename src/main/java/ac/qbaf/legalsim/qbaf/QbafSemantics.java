package ac.qbaf.legalsim.qbaf;

import ac.qbaf.legalsim.qbaf.QbafGraph.Decision;
import ac.qbaf.legalsim.qbaf.QbafGraph.Support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * חישוב הכרעה על גרף QBAF.
 * <p>
 * עוצמת קודקוד החלטה = Σ w על כל קשתות התמיכה שנכנסות אליו.
 * סכום ליניארי, כדי ש-WeightCalibrator יוכל לכייל את המשקלים ב-Linear Programming.
 */
public class QbafSemantics {
    public static final String UNDECIDED = "undecided";

    private static final double EPSILON = 1e-9;

    /** עוצמה לכל קודקוד החלטה. */
    public Map<String, Double> score(QbafGraph graph) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Decision decision : graph.decisions()) {
            double total = 0.0;
            for (Support support : graph.supportersOf(decision.id())) {
                if (support.weight() == null) {
                    throw new IllegalStateException("The judge has not weighed the edge of " + support.argument());
                }
                total += support.weight();
            }
            scores.put(decision.id(), total);
        }
        return scores;
    }

    /** מחזיר את מזהה קודקוד ההחלטה המוביל, או undecided בתיקו. */
    public String leadingDecision(QbafGraph graph) {
        String leader = UNDECIDED;
        double best = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> entry : score(graph).entrySet()) {
            double value = entry.getValue();
            if (value > best + EPSILON) {
                leader = entry.getKey();
                best = value;
            } else if (Math.abs(value - best) <= EPSILON) {
                leader = UNDECIDED;
            }
        }
        return leader;
    }
}
