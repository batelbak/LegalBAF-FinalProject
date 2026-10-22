package ac.qbaf.legalsim.qbaf;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * גרף טיעונים כמותי (QBAF).
 * קודקודי החלטה = צמתי הכרעה מוגדרים מראש (למשל הרשעה / זיכוי).
 * קודקודי טיעון = טענות שעלו מהסוכנים בדיון.
 * קשתות תמיכה בלבד: מטיעון אל קודקוד ההחלטה שהוא מחזק, עם משקל = עוצמת הטיעון.
 */
public class QbafGraph {

    /** קודקוד החלטה, למשל "conviction" / "acquittal". */
    public record Decision(String id, String label) {
    }

    /** קודקוד טיעון, למשל "אנשי ציבור חייבים לסבול ביקורת חריפה", עם הקטגוריה שלו. */
    public record Argument(String id, String text, ArgumentCategory category) {
    }

    /** קשת תמיכה מכוונת: argument → decision, במשקל בטווח [0, 1]. */
    public record Support(String argument, String decision, double weight) {
    }

    private final Map<String, Decision> decisions = new LinkedHashMap<>();
    private final Map<String, Argument> arguments = new LinkedHashMap<>();
    private final Map<String, Support> supports = new LinkedHashMap<>();

    public void addDecision(String decisionId, String label) {
        requireNewId(decisionId);
        decisions.put(decisionId, new Decision(decisionId, label));
    }

    public void addArgument(String argumentId, String text) {
        addArgument(argumentId, text, ArgumentCategory.OTHER);
    }

    public void addArgument(String argumentId, String text, ArgumentCategory category) {
        requireNewId(argumentId);
        arguments.put(argumentId, new Argument(argumentId, text, category));
    }

    public Argument argument(String argumentId) {
        Argument argument = arguments.get(argumentId);
        if (argument == null) {
            throw new IllegalArgumentException("Unknown argument: " + argumentId);
        }
        return argument;
    }

    /** כל טיעון תומך בקודקוד החלטה אחד בלבד. */
    public void addSupport(String argumentId, String decisionId, double weight) {
        if (!arguments.containsKey(argumentId)) {
            throw new IllegalArgumentException("Unknown argument: " + argumentId);
        }
        if (!decisions.containsKey(decisionId)) {
            throw new IllegalArgumentException("Unknown decision: " + decisionId);
        }
        if (!(weight >= 0.0 && weight <= 1.0)) {
            throw new IllegalArgumentException("weight must be in [0, 1], got " + weight);
        }
        if (supports.containsKey(argumentId)) {
            throw new IllegalArgumentException("Argument already supports a decision: " + argumentId);
        }
        supports.put(argumentId, new Support(argumentId, decisionId, weight));
    }

    public Collection<Decision> decisions() {
        return Collections.unmodifiableCollection(decisions.values());
    }

    public Collection<Argument> arguments() {
        return Collections.unmodifiableCollection(arguments.values());
    }

    public Collection<Support> supports() {
        return Collections.unmodifiableCollection(supports.values());
    }

    public List<Support> supportersOf(String decisionId) {
        if (!decisions.containsKey(decisionId)) {
            throw new IllegalArgumentException("Unknown decision: " + decisionId);
        }
        return supports.values().stream()
                .filter(s -> s.decision().equals(decisionId))
                .toList();
    }

    private void requireNewId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (decisions.containsKey(id) || arguments.containsKey(id)) {
            throw new IllegalArgumentException("Node already exists: " + id);
        }
    }
}
