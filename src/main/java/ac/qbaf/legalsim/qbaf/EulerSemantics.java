package ac.qbaf.legalsim.qbaf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Restricted Euler-based semantics (Amgoud &amp; Ben-Naim 2017) לגרפים חסרי מעגלים, IJAR 2019 סעיף 5.9,
 * משוואות 35–36:
 * <pre>
 * σ(α) = 1 − (1 − τ(α)²) / (1 + τ(α)·e^E),   E = Σ σ(תומכים) − Σ σ(תוקפים)
 * </pre>
 * מאוזנת ומונוטונית באופן חמור (Proposition 38): כל תוקף או תומך נוסף משנה את העוצמה, בלי רוויה.
 * משמשת להשוואה מול DF-QuAD — אם ההכרעה תלויה בבחירת הסמנטיקה, התיק שביר.
 * שימו לב: עם τ(C) = 0.5 ובלי טיעונים, σ(C) = 0.5, ולכן אותו סף הכרעה תקף לשתיהן.
 */
public class EulerSemantics implements GradualSemantics {

    @Override
    public String id() {
        return "euler";
    }

    @Override
    public String name() {
        return "Euler-based";
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
        Double tau = graph.argument(id).baseScore();
        if (tau == null) {
            throw new IllegalStateException("The judge has not scored " + id);
        }
        double energy = 0.0;
        for (String s : graph.supporters(id)) {
            energy += strength(graph, s, memo);
        }
        for (String a : graph.attackers(id)) {
            energy -= strength(graph, a, memo);
        }
        double value = 1 - (1 - tau * tau) / (1 + tau * Math.exp(energy));
        memo.put(id, value);
        return value;
    }
}
