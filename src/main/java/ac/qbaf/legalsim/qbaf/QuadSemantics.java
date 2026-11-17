package ac.qbaf.legalsim.qbaf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * אלגוריתם QuAD המקורי (Baroni et al. 2015, סעיף 3; IJAR 2019 סעיף 5.8, משוואות 17–26):
 * <pre>
 * σ(α) = g(τ(α), Fa(τ(α), σ(R⁻(α))), Fs(τ(α), σ(R⁺(α))))
 *
 * fa(v0, v) = v0·(1 − v)              fs(v0, v) = v0 + (1 − v0)·v
 * F×(v0, S) = nil אם אין ב-S ערך שונה מאפס; אחרת מפעילים את f× ברצף על הערכים שאינם אפס, החל מ-v0
 *
 * g(v0, va, vs) = va            אם vs = nil
 * g(v0, va, vs) = vs            אם va = nil
 * g(v0, va, vs) = v0            אם שניהם nil
 * g(v0, va, vs) = (va + vs) / 2 אחרת
 * </pre>
 * מונוטונית אבל <b>לא מאוזנת</b> (Proposition 36 והדוגמה שאחריה), ויש בה אי-רציפות: טיעון עם תוקפים חזקים
 * ובלי אף תומך יכול לרדת ל-0, ותומך חלש אחד מקפיץ אותו לפחות ל-τ/2 (QuAD סעיף 4.2). DF-QuAD תוכננה כדי
 * לתקן את זה, ולכן היא ברירת המחדל; QuAD כאן להשוואה ולשחזור התוצאות של המאמר.
 */
public class QuadSemantics implements GradualSemantics {

    @Override
    public String id() {
        return "quad";
    }

    @Override
    public String name() {
        return "QuAD";
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
        List<Double> attackers = effective(graph.attackers(id), graph, memo);
        List<Double> supporters = effective(graph.supporters(id), graph, memo);

        double value;
        if (attackers.isEmpty() && supporters.isEmpty()) {
            value = tau;
        } else {
            double va = tau;
            for (double v : attackers) {
                va = va * (1 - v);
            }
            double vs = tau;
            for (double v : supporters) {
                vs = vs + (1 - vs) * v;
            }
            value = supporters.isEmpty() ? va : attackers.isEmpty() ? vs : (va + vs) / 2;
        }
        memo.put(id, value);
        return value;
    }

    /** העוצמות שאינן אפס: תוקף או תומך בעוצמה 0 "לא משפיע ואפשר להתעלם ממנו" (QuAD סעיף 4.1). */
    private List<Double> effective(List<String> ids, QbafGraph graph, Map<String, Double> memo) {
        List<Double> values = new ArrayList<>();
        for (String id : ids) {
            double v = strength(graph, id, memo);
            if (v != 0.0) {
                values.add(v);
            }
        }
        return values;
    }
}
