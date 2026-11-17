package ac.qbaf.legalsim.qbaf;

import ac.qbaf.legalsim.data.LegalCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ההכרעה על גרף QBAF: מריצים סמנטיקה הדרגתית ובודקים את העוצמה הסופית של הטענה המרכזית C מול ציון
 * הבסיס הניטרלי שלה.
 * <p>
 * σ(C) &gt; τ(C) = 0.5 — הטיעונים בעד הטענה גברו: violation.
 * σ(C) &lt; 0.5 — הטיעונים נגדה גברו: no_violation.
 * σ(C) = 0.5 — undecided. בסמנטיקה מאוזנת זה קורה כשהתוקפים והתומכים של C שקולים (עיקרון Balance).
 * <p>
 * הסמנטיקה נבחרת במשתנה הסביבה LEGALSIM_SEMANTICS: df-quad (ברירת מחדל), quad או euler.
 */
public class QbafSemantics {
    public static final String UNDECIDED = "undecided";
    public static final String ENV = "LEGALSIM_SEMANTICS";

    private static final double EPSILON = 1e-9;

    /** strengths: σ לכל קודקוד. claimStrength: σ(C). leading: ההכרעה. */
    public record Ruling(Map<String, Double> strengths, double claimStrength, String leading) {
    }

    /** ההכרעה תחת סמנטיקה אחרת, להשוואה: אם ההכרעה תלויה בבחירת הסמנטיקה, התיק שביר. */
    public record Alternative(String name, double claimStrength, String leading) {
    }

    private final GradualSemantics semantics;

    public QbafSemantics() {
        this(configured());
    }

    public QbafSemantics(GradualSemantics semantics) {
        this.semantics = semantics;
    }

    /** שלוש הסמנטיקות שהמאמר מגדיר ל-QBAF כללי על [0,1]. */
    public static List<GradualSemantics> all() {
        return List.of(new DfQuadSemantics(), new QuadSemantics(), new EulerSemantics());
    }

    public static GradualSemantics configured() {
        return byId(System.getenv(ENV));
    }

    /** id לא מוכר או ריק: DF-QuAD. */
    public static GradualSemantics byId(String id) {
        if (id != null) {
            String wanted = id.strip().toLowerCase().replace("_", "-");
            for (GradualSemantics s : all()) {
                if (s.id().equals(wanted) || s.id().replace("-", "").equals(wanted)) {
                    return s;
                }
            }
        }
        return new DfQuadSemantics();
    }

    public String semanticsName() {
        return semantics.name();
    }

    public Ruling rule(QbafGraph graph) {
        Map<String, Double> strengths = semantics.strengths(graph);
        double claim = strengths.get(QbafGraph.CLAIM);
        return new Ruling(strengths, claim, decisionFor(claim));
    }

    /** σ(C) וההכרעה תחת כל אחת מהסמנטיקות, לפי id. */
    public static Map<String, Alternative> alternatives(QbafGraph graph) {
        Map<String, Alternative> result = new LinkedHashMap<>();
        for (GradualSemantics s : all()) {
            double claim = s.strengths(graph).get(QbafGraph.CLAIM);
            result.put(s.id(), new Alternative(s.name(), claim, decisionFor(claim)));
        }
        return result;
    }

    public static String decisionFor(double claimStrength) {
        if (claimStrength > QbafGraph.NEUTRAL + EPSILON) {
            return LegalCase.VIOLATION;
        }
        if (claimStrength < QbafGraph.NEUTRAL - EPSILON) {
            return LegalCase.NO_VIOLATION;
        }
        return UNDECIDED;
    }
}
