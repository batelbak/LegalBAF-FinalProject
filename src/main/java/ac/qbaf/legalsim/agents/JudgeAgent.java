package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.llm.Claude;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafSemantics;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * סוכן השופט: לא מגיב במהלך הדיון. בסוף הוא מסווג כל טיעון לקטגוריה, נותן לו ציון,
 * ממפה את הטיעונים לגרף QBAF ומכריע מי מוביל.
 */
public class JudgeAgent extends Agent {
    public static final String SIDE_APPLICANT = "applicant";
    public static final String SIDE_GOVERNMENT = "government";

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
            @JsonPropertyDescription("One or two sentences explaining the category and the strength")
            String rationale) {
    }

    public record Assessment(List<ArgumentScore> scores) {
    }

    public record ScoredArgument(PartyArgument argument, ArgumentCategory category, double strength,
                                 String rationale) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a judge of the European Court of Human Rights examining an Article 10 \
            (freedom of expression) complaint concerning the press and defamation. You receive the \
            facts and the arguments of the applicant (who claims a violation) and of the Government \
            (who claims no violation). You do not decide the case here. For every argument:

            1. category: classify it into exactly one of these categories:
            %s
            2. strength: rate from 0.0 to 1.0 how much legal weight the argument deserves on the \
            facts of this case under the Court's Article 10 case-law. 0.0 = irrelevant or unfounded, \
            0.5 = relevant but contestable, 1.0 = compelling. Judge each argument on its own merits; \
            do not lower or raise a rating because of which side is likely to win.
            3. rationale: one or two sentences explaining the category and the rating.

            Return exactly one score for every argument id."""
            .formatted(Arrays.stream(ArgumentCategory.values())
                    .map(c -> "- " + c.id() + ": " + c.description())
                    .collect(Collectors.joining("\n")));

    private final Function<String, Assessment> llm;

    public JudgeAgent() {
        this(message -> Claude.structured(SYSTEM_PROMPT, message, Assessment.class));
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

    /** הטענות האמיתיות מפסק הדין: A1, A2... של המבקש ו-G1, G2... של הממשלה. */
    public static List<PartyArgument> submissionsOf(LegalCase legalCase) {
        List<PartyArgument> arguments = new ArrayList<>();
        for (int i = 0; i < legalCase.applicantSubmissions().size(); i++) {
            arguments.add(new PartyArgument("A" + (i + 1), SIDE_APPLICANT, legalCase.applicantSubmissions().get(i)));
        }
        for (int i = 0; i < legalCase.governmentSubmissions().size(); i++) {
            arguments.add(new PartyArgument("G" + (i + 1), SIDE_GOVERNMENT, legalCase.governmentSubmissions().get(i)));
        }
        return arguments;
    }

    public List<ScoredArgument> assess(String facts, List<PartyArgument> arguments) {
        StringBuilder message = new StringBuilder("<facts>\n").append(facts).append("\n</facts>\n\n<arguments>\n");
        for (PartyArgument argument : arguments) {
            message.append(argument.id()).append(" (").append(argument.side()).append("): ")
                    .append(argument.text()).append('\n');
        }
        message.append("</arguments>");

        Map<String, ArgumentScore> byId = new HashMap<>();
        for (ArgumentScore score : llm.apply(message.toString()).scores()) {
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
                    score.rationale()));
        }
        return scored;
    }

    /** משקל קשת = ציון השופט × θ של הקטגוריה. קטגוריה בלי θ מקבלת 1. */
    public QbafGraph buildGraph(List<ScoredArgument> scored, Map<ArgumentCategory, Double> theta) {
        QbafGraph graph = new QbafGraph();
        graph.addDecision(LegalCase.VIOLATION, "Violation of Article 10");
        graph.addDecision(LegalCase.NO_VIOLATION, "No violation of Article 10");
        for (ScoredArgument s : scored) {
            String decision = SIDE_APPLICANT.equals(s.argument().side()) ? LegalCase.VIOLATION : LegalCase.NO_VIOLATION;
            graph.addArgument(s.argument().id(), s.argument().text(), s.category());
            graph.addSupport(s.argument().id(), decision, s.strength() * theta.getOrDefault(s.category(), 1.0));
        }
        return graph;
    }

    public QbafGraph mapToGraph(List<Map<String, Object>> history) {
        throw new UnsupportedOperationException("skeleton");
    }

    public Map<String, Object> decide(QbafGraph graph) {
        QbafSemantics semantics = new QbafSemantics();
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("scores", semantics.score(graph));
        decision.put("leading", semantics.leadingDecision(graph));
        return decision;
    }
}
