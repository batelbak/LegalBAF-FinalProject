package ac.qbaf.legalsim.qbaf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * בודק על גרף נתון את שני העקרונות של Baroni, Rago &amp; Toni (IJAR 2019, סעיף 4), שמהם נגזרות כל 11
 * קבוצות התכונות (GP1–GP11) שבמאמר:
 * <ul>
 *   <li><b>Balance</b> (Principle 1): הפער בין העוצמה לציון הבסיס משקף את חוסר האיזון בין התוקפים לתומכים.
 *   קבוצות שקולות ⇒ σ = τ; תוקפים חזקים יותר ⇒ σ ≤ τ; תומכים חזקים יותר ⇒ σ ≥ τ.</li>
 *   <li><b>Monotonicity</b> (Principle 2): העוצמה תלויה מונוטונית בציון הבסיס, בתומכים ובתוקפים.
 *   "שלשה מעצבת" (Def. 5) מחזקת לפחות באותה מידה ⇒ עוצמה לא נמוכה יותר.</li>
 * </ul>
 * ההשוואה בין קבוצות היא ההשוואה של Definition 3 בגרסת σ/⊥ (טיעונים בעוצמה 0 לא נספרים, כמו ב-QuAD,
 * DF-QuAD ו-Euler): A ≥ B אם יש התאמה חד-חד-ערכית מ-B אל A שבה כל איבר של B מותאם לאיבר חזק לפחות כמוהו.
 * הגרסאות כאן הן הלא-חמורות (≪ = ≤), שהן אלה שהמאמר מוכיח ל-DF-QuAD (Proposition 37).
 * <p>
 * משמש בבדיקות: DF-QuAD ו-Euler חייבות לעמוד בשניהם על כל גרף; QuAD רק במונוטוניות.
 */
public final class Principles {
    private static final double EPSILON = 1e-9;

    /** הפרות של Balance. רשימה ריקה: העיקרון מתקיים בגרף הזה. */
    public static List<String> balanceViolations(QbafGraph graph, Map<String, Double> sigma) {
        List<String> violations = new ArrayList<>();
        for (QbafGraph.Argument a : graph.arguments()) {
            List<Double> attackers = effective(graph.attackers(a.id()), sigma);
            List<Double> supporters = effective(graph.supporters(a.id()), sigma);
            double tau = a.baseScore();
            double s = sigma.get(a.id());
            if (equivalent(attackers, supporters)) {
                if (Math.abs(s - tau) > EPSILON) {
                    violations.add(a.id() + ": attackers and supporters are equivalent but σ=" + s + " ≠ τ=" + tau);
                }
            } else if (stronger(attackers, supporters)) {
                if (s > tau + EPSILON) {
                    violations.add(a.id() + ": attackers are stronger but σ=" + s + " > τ=" + tau);
                }
            } else if (stronger(supporters, attackers)) {
                if (s < tau - EPSILON) {
                    violations.add(a.id() + ": supporters are stronger but σ=" + s + " < τ=" + tau);
                }
            }
        }
        return violations;
    }

    /** הפרות של Monotonicity, על כל זוגות הקודקודים. רשימה ריקה: העיקרון מתקיים בגרף הזה. */
    public static List<String> monotonicityViolations(QbafGraph graph, Map<String, Double> sigma) {
        List<String> violations = new ArrayList<>();
        List<QbafGraph.Argument> nodes = List.copyOf(graph.arguments());
        for (QbafGraph.Argument a : nodes) {
            for (QbafGraph.Argument b : nodes) {
                if (a == b) {
                    continue;
                }
                List<Double> aPlus = effective(graph.supporters(a.id()), sigma);
                List<Double> bPlus = effective(graph.supporters(b.id()), sigma);
                List<Double> aMinus = effective(graph.attackers(a.id()), sigma);
                List<Double> bMinus = effective(graph.attackers(b.id()), sigma);
                // ST(a) ⪯ ST(b): τ(a) ≤ τ(b), R⁺(a) ≤ R⁺(b), R⁻(b) ≤ R⁻(a)
                boolean bBoostsAtLeast = a.baseScore() <= b.baseScore() + EPSILON
                        && atLeastAsStrong(bPlus, aPlus) && atLeastAsStrong(aMinus, bMinus);
                if (bBoostsAtLeast && sigma.get(a.id()) > sigma.get(b.id()) + EPSILON) {
                    violations.add(b.id() + " is at least as boosted as " + a.id() + " but σ(" + a.id() + ")="
                            + sigma.get(a.id()) + " > σ(" + b.id() + ")=" + sigma.get(b.id()));
                }
            }
        }
        return violations;
    }

    /** העוצמות שאינן אפס, מהגדולה לקטנה (σ/⊥). */
    static List<Double> effective(List<String> ids, Map<String, Double> sigma) {
        return ids.stream().map(sigma::get).filter(v -> v > EPSILON).sorted(Comparator.reverseOrder()).toList();
    }

    /** A ≥ B לפי Definition 3. על רשימות ממוינות: האיבר ה-i של A גדול או שווה לאיבר ה-i של B. */
    static boolean atLeastAsStrong(List<Double> a, List<Double> b) {
        if (b.size() > a.size()) {
            return false;
        }
        for (int i = 0; i < b.size(); i++) {
            if (a.get(i) < b.get(i) - EPSILON) {
                return false;
            }
        }
        return true;
    }

    static boolean equivalent(List<Double> a, List<Double> b) {
        return a.size() == b.size() && atLeastAsStrong(a, b) && atLeastAsStrong(b, a);
    }

    static boolean stronger(List<Double> a, List<Double> b) {
        return atLeastAsStrong(a, b) && !atLeastAsStrong(b, a);
    }

    private Principles() {
    }
}
