package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.llm.Llm;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafSemantics;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * סוכן השופט: לא מגיב במהלך הדיון ולא מכניס טיעונים לגרף. את זה עושים סוכני התביעה וההגנה.
 * השופט קורא את הטיעונים שבגרף, מסווג כל טיעון לקטגוריה וקובע את ציון הבסיס שלו τ ∈ [0,1] —
 * ההערכה של הטיעון כשלעצמו, לפני היחסים שלו עם טיעונים אחרים (IJAR 2019, הערה להגדרה 1).
 * ההכרעה מחושבת מהגרף בסמנטיקה הדרגתית (DF-QuAD): העוצמה הסופית של הטענה המרכזית C קובעת.
 */
public class JudgeAgent extends Agent {
    /** טיעון של צד, כפי שהוא מוצג לשופט. */
    public record PartyArgument(String id, String side, String text) {
    }

    public record ArgumentScore(
            @JsonPropertyDescription("The argument id exactly as given, e.g. A1 or G3")
            String argumentId,
            @JsonPropertyDescription("Exactly one category id from the list")
            String category,
            @JsonPropertyDescription("Legal weight of the argument on the facts of this case, from 0.0 to 1.0")
            double strength,
            @JsonPropertyDescription("Normally an empty string. Only if this argument makes exactly the same "
                    + "point as an EARLIER argument of the same side, the id of that earlier argument")
            String duplicateOf,
            @JsonPropertyDescription("One or two sentences explaining the category and the strength")
            String rationale) {

        public ArgumentScore(String argumentId, String category, double strength, String rationale) {
            this(argumentId, category, strength, "", rationale);
        }
    }

    public record Assessment(List<ArgumentScore> scores) {
    }

    /**
     * ההערכה של השופט לטיעון אחד. duplicateOf: מזהה של טיעון קודם של אותו צד שהטיעון הזה חוזר עליו בדיוק,
     * או null. טיעון כפול ממוזג אל המקור (QbafGraph.merge) ולא נספר פעמיים.
     */
    public record ScoredArgument(PartyArgument argument, ArgumentCategory category, double strength,
                                 String rationale, String duplicateOf) {

        public ScoredArgument(PartyArgument argument, ArgumentCategory category, double strength, String rationale) {
            this(argument, category, strength, rationale, null);
        }

        public ScoredArgument withoutDuplicate() {
            return new ScoredArgument(argument, category, strength, rationale, null);
        }
    }

    private static final String SYSTEM_PROMPT = """
            You are a judge of the European Court of Human Rights examining an Article 10 \
            (freedom of expression) complaint concerning the press and defamation. You receive the \
            facts and the arguments of the applicant (who claims a violation) and of the Government \
            (who claims no violation). The arguments form a graph: each one supports or attacks the \
            central claim C and/or other arguments, as shown. You do not decide the case here: the decision is \
            computed from the graph, so a strong rebuttal weakens the argument it attacks automatically. \
            For every argument:

            1. category: classify it into exactly one of these categories:
            %s
            2. strength: rate from 0.0 to 1.0 the intrinsic legal weight of the argument on the facts of \
            this case under the Court's Article 10 case-law, as a reply to its targets. Do not lower it merely \
            because another argument attacks it: that effect is computed separately. 0.5 = relevant but \
            contestable. The two extremes are deliberate choices with a special effect: exactly 0.0 means the \
            argument is irrelevant or unfounded and has no effect at all; exactly 1.0 means it is decisive and \
            overrides everything else on its targets. Judge each argument on its own merits; do not lower or \
            raise a rating because of which side is likely to win.
            3. duplicateOf: normally an empty string. If the argument makes exactly the same point as an \
            EARLIER argument of the same side (a restatement, not merely a related point), give that earlier \
            argument's id. A duplicate is merged into the earlier argument so the same point is not counted twice.
            4. rationale: one or two sentences explaining the category and the rating.

            Return exactly one score for every argument id."""
            .formatted(Arrays.stream(ArgumentCategory.values())
                    .map(c -> "- " + c.id() + ": " + c.description())
                    .collect(Collectors.joining("\n")));

    /** כמה פעמים לבקש הערכה מחדש כשהתשובה לא תקינה (טיעון חסר, קטגוריה לא מוכרת, חוזק מחוץ לטווח). */
    static final int ATTEMPTS = 3;

    private final Function<String, Assessment> llm;
    private final QbafSemantics semantics = new QbafSemantics();

    public JudgeAgent() {
        this(message -> Llm.structured(SYSTEM_PROMPT, message, Assessment.class, Llm.PRECISE));
    }

    /** llm: מקבל את ההודעה לשופט ומחזיר את ההערכה. בטסטים זו פונקציה מזויפת. */
    public JudgeAgent(Function<String, Assessment> llm) {
        this.llm = llm;
    }

    @Override
    public String role() {
        return "judge";
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public Map<String, Object> respond(Map<String, Object> legalCase, List<Map<String, Object>> history) {
        throw new UnsupportedOperationException("The judge does not respond during the hearing");
    }

    /** הטיעונים שהסוכנים הכניסו לגרף, בלי הטענה המרכזית. */
    public static List<PartyArgument> argumentsIn(QbafGraph graph) {
        List<PartyArgument> arguments = new ArrayList<>();
        for (QbafGraph.Argument argument : graph.partyArguments()) {
            arguments.add(new PartyArgument(argument.id(), argument.side(), argument.text()));
        }
        return arguments;
    }

    /** הערכה של טיעונים בלי הקשר של גרף (כל טיעון מוצג לבדו). */
    public List<ScoredArgument> assess(String facts, List<PartyArgument> arguments) {
        return assess(facts, arguments, null);
    }

    /**
     * הערכה של הטיעונים arguments. graph (אופציונלי) מספק את ההקשר: על מה כל טיעון עונה ומה כבר נטען.
     * בשורה של כל טיעון מופיעים הצד והיחס, למשל "D1 (government) attacks P1: ...".
     */
    public List<ScoredArgument> assess(String facts, List<PartyArgument> arguments, QbafGraph graph) {
        StringBuilder message = new StringBuilder("<facts>\n").append(facts).append("\n</facts>\n\n");
        if (graph != null) {
            message.append("<graph>\n").append(PartyAgent.describe(graph)).append("</graph>\n\n");
        }
        message.append("<arguments to assess>\n");
        for (PartyArgument argument : arguments) {
            message.append(argument.id()).append(" (").append(argument.side()).append(")");
            if (graph != null && graph.contains(argument.id())) {
                message.append(' ').append(PartyAgent.describeEdges(graph.edgesFrom(argument.id())));
            }
            message.append(": ").append(argument.text()).append('\n');
        }
        message.append("</arguments to assess>");

        // מודל מקומי קטן לפעמים מדלג על טיעון או ממציא קטגוריה: מנסים שוב לפני שנכשלים
        RuntimeException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return validate(arguments, llm.apply(message.toString()));
            } catch (IllegalStateException | IllegalArgumentException e) {
                last = e;
            }
        }
        throw last;
    }

    private static List<ScoredArgument> validate(List<PartyArgument> arguments, Assessment assessment) {
        Map<String, ArgumentScore> byId = new HashMap<>();
        for (ArgumentScore score : assessment.scores()) {
            byId.put(score.argumentId(), score);
        }

        List<ScoredArgument> scored = new ArrayList<>();
        for (PartyArgument argument : arguments) {
            ArgumentScore score = byId.get(argument.id());
            if (score == null) {
                throw new IllegalStateException("Judge returned no score for " + argument.id());
            }
            if (!(score.strength() >= 0.0 && score.strength() <= 1.0)) {
                throw new IllegalStateException("Strength out of range for " + argument.id() + ": " + score.strength());
            }
            scored.add(new ScoredArgument(argument, ArgumentCategory.fromId(score.category()), score.strength(),
                    score.rationale(), duplicateId(score.duplicateOf(), argument.id())));
        }
        return scored;
    }

    /** מזהה הכפילות כפי שהמודל החזיר אותו, או null כשאין (ריק, "none", או הטיעון עצמו). */
    private static String duplicateId(String raw, String ownId) {
        if (raw == null) {
            return null;
        }
        String id = raw.strip();
        if (id.isEmpty() || id.equals(ownId) || id.equalsIgnoreCase("none") || id.equalsIgnoreCase("null")) {
            return null;
        }
        return id;
    }

    /**
     * קובע את ציון הבסיס של טיעון אחד: τ = ציון השופט × θ של הקטגוריה, חסום ל-[0,1].
     * קטגוריה בלי θ מקבלת 1 (כמו בשלב א').
     */
    public void score(QbafGraph graph, ScoredArgument s, Map<ArgumentCategory, Double> theta) {
        double tau = Math.min(1.0, Math.max(0.0, s.strength() * theta.getOrDefault(s.category(), 1.0)));
        graph.score(s.argument().id(), s.category(), tau);
    }

    /** קובע את ציון הבסיס של כל הטיעונים בגרף. לכל טיעון בגרף חייבת להיות הערכה. */
    public void weigh(QbafGraph graph, List<ScoredArgument> scored, Map<ArgumentCategory, Double> theta) {
        Map<String, ScoredArgument> byId = new HashMap<>();
        for (ScoredArgument s : scored) {
            byId.put(s.argument().id(), s);
        }
        for (QbafGraph.Argument argument : graph.partyArguments()) {
            ScoredArgument s = byId.get(argument.id());
            if (s == null) {
                throw new IllegalStateException("No assessment for " + argument.id());
            }
            score(graph, s, theta);
        }
    }

    /** ההכרעה על הגרף בסמנטיקה שנבחרה (ברירת מחדל DF-QuAD): σ לכל קודקוד, σ(C), ומי מוביל. */
    public QbafSemantics.Ruling decide(QbafGraph graph) {
        return semantics.rule(graph);
    }

    /** שם הסמנטיקה שלפיה השופט מכריע. */
    public String semanticsName() {
        return semantics.semanticsName();
    }
}
