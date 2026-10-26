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
 * <p>
 * חלוקת התפקידים: סוכני התביעה וההגנה מוסיפים טיעונים וקשתות בלי משקל (addArgument, addSupport).
 * רק השופט מסווג את הטיעון וקובע את משקל הקשת (weigh).
 */
public class QbafGraph {

    /** קודקוד החלטה, למשל "conviction" / "acquittal". */
    public record Decision(String id, String label) {
    }

    /** קודקוד טיעון, למשל "אנשי ציבור חייבים לסבול ביקורת חריפה". category == null עד שהשופט מסווג. */
    public record Argument(String id, String text, ArgumentCategory category) {
    }

    /** קשת תמיכה מכוונת: argument → decision. weight בטווח [0, 1], או null עד שהשופט שוקל אותה. */
    public record Support(String argument, String decision, Double weight) {
    }

    private final Map<String, Decision> decisions = new LinkedHashMap<>();
    private final Map<String, Argument> arguments = new LinkedHashMap<>();
    private final Map<String, Support> supports = new LinkedHashMap<>();

    public void addDecision(String decisionId, String label) {
        requireNewId(decisionId);
        decisions.put(decisionId, new Decision(decisionId, label));
    }

    public void addArgument(String argumentId, String text) {
        requireNewId(argumentId);
        arguments.put(argumentId, new Argument(argumentId, text, null));
    }

    public Argument argument(String argumentId) {
        Argument argument = arguments.get(argumentId);
        if (argument == null) {
            throw new IllegalArgumentException("Unknown argument: " + argumentId);
        }
        return argument;
    }

    /** קשת בלי משקל. כל טיעון תומך בקודקוד החלטה אחד בלבד. */
    public void addSupport(String argumentId, String decisionId) {
        argument(argumentId);
        if (!decisions.containsKey(decisionId)) {
            throw new IllegalArgumentException("Unknown decision: " + decisionId);
        }
        if (supports.containsKey(argumentId)) {
            throw new IllegalArgumentException("Argument already supports a decision: " + argumentId);
        }
        supports.put(argumentId, new Support(argumentId, decisionId, null));
    }

    /** הכרעת השופט על טיעון: הקטגוריה שלו והמשקל של קשת התמיכה שלו. */
    public void weigh(String argumentId, ArgumentCategory category, double weight) {
        Argument argument = argument(argumentId);
        Support support = supports.get(argumentId);
        if (support == null) {
            throw new IllegalArgumentException("Argument supports no decision: " + argumentId);
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (!(weight >= 0.0 && weight <= 1.0)) {
            throw new IllegalArgumentException("weight must be in [0, 1], got " + weight);
        }
        arguments.put(argumentId, new Argument(argumentId, argument.text(), category));
        supports.put(argumentId, new Support(argumentId, support.decision(), weight));
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
