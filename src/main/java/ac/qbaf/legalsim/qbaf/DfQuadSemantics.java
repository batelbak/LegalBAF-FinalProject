package ac.qbaf.legalsim.qbaf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DF-QuAD (Rago, Toni, Aurisicchio &amp; Baroni, KR 2016), כפי שמוגדר ב-IJAR 2019, סעיף 5.8, משוואות 27–34:
 * <pre>
 * σ(α) = c(τ(α), F'(σ(R⁻(α))), F'(σ(R⁺(α))))
 *
 * F'()          = 0
 * F'(v)         = v
 * F'(v1,…,vn)   = f'(F'(v1,…,vn-1), vn),   f'(a, b) = a + b − a·b     (צבירה "רוויה" של עוצמות)
 *
 * c(v0, va, vs) = v0 − v0·|vs − va|        אם va ≥ vs    (התוקפים חזקים יותר: יורד לכיוון 0)
 * c(v0, va, vs) = v0 + (1 − v0)·|vs − va|  אם va &lt; vs    (התומכים חזקים יותר: עולה לכיוון 1)
 * </pre>
 * לפי Proposition 37 הסמנטיקה מאוזנת (balanced) ומונוטונית: כשהתוקפים והתומכים שקולים σ = τ,
 * ותוקף חזק יותר / תומך חלש יותר לעולם לא מעלים את העוצמה. f' לא תלוי בסדר, כך שהתוצאה לא תלויה
 * בסדר שבו נטענו הטיעונים. הגרף חסר מעגלים, ולכן מחשבים מהעלים אל השורש.
 */
public class DfQuadSemantics implements GradualSemantics {

    @Override
    public String id() {
        return "df-quad";
    }

    @Override
    public String name() {
        return "DF-QuAD";
    }

    @Override
    public Map<String, Double> strengths(QbafGraph graph) {
        Map<String, Double> memo = new HashMap<>();
        Map<String, Double> result = new LinkedHashMap<>();
        for (QbafGraph.Argument argument : graph.arguments()) {
            result.put(argument.id(), strength(graph, argument.id(), memo));
        }
        return result;
    }

    private double strength(QbafGraph graph, String id, Map<String, Double> memo) {
        Double known = memo.get(id);
        if (known != null) {
            return known;
        }
        QbafGraph.Argument argument = graph.argument(id);
        if (argument.baseScore() == null) {
            throw new IllegalStateException("The judge has not scored " + id);
        }
        double va = aggregate(graph.attackers(id), graph, memo);
        double vs = aggregate(graph.supporters(id), graph, memo);
        double value = combine(argument.baseScore(), va, vs);
        memo.put(id, value);
        return value;
    }

    private double aggregate(List<String> ids, QbafGraph graph, Map<String, Double> memo) {
        double total = 0.0;
        for (String id : ids) {
            total = total + strength(graph, id, memo) - total * strength(graph, id, memo);
        }
        return total;
    }

    /** פונקציית הקוטביות c (משוואות 28–29). */
    static double combine(double v0, double va, double vs) {
        double diff = Math.abs(vs - va);
        return va >= vs ? v0 - v0 * diff : v0 + (1 - v0) * diff;
    }
}
