package ac.qbaf.legalsim.qbaf;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quantitative Bipolar Argumentation Framework (QBAF) לפי Baroni, Rago &amp; Toni (IJAR 2019), הגדרה 1:
 * רביעייה ⟨X, R⁻, R⁺, τ⟩ — קבוצת טיעונים X, יחס התקפה R⁻, יחס תמיכה R⁺ ופונקציית ציון בסיס τ: X → [0,1].
 * (docs/Baroni-Rago-Toni-2019-QBAF-Gradual-Argumentation-IJAR.pdf)
 * <p>
 * כמו ב-QuAD (Baroni et al. 2015, הגדרה 2.1) היחס הוא <b>כל יחס חסר מעגלים</b>, לא רק עץ:
 * טיעון אחד יכול לתקוף או לתמוך בכמה קודקודים (באיור 3 שם P1 תומך גם ב-A1 וגם ב-A2, ו-C1 תוקף גם את A2
 * וגם את P1), וקודקוד יכול לקבל כמה תוקפים ותומכים. בשורש יש הטענה המרכזית C — "ההתערבות הפרה את סעיף 10".
 * <p>
 * אינווריאנטים שהמחלקה אוכפת:
 * <ul>
 *   <li>אין מעגלים: קשת נדחית אם היעד שלה כבר מגיע אל המקור.</li>
 *   <li>לכל טיעון יש לפחות קשת יוצאת אחת, ולכן כל טיעון מגיע בסוף אל C.</li>
 *   <li>בין שני קודקודים יש לכל היותר קשת אחת (טיעון לא יכול גם לתקוף וגם לתמוך באותו יעד).</li>
 *   <li>ל-C אין קשתות יוצאות, וציון הבסיס שלה קבוע וניטרלי.</li>
 * </ul>
 * חלוקת התפקידים: הסוכנים מוסיפים טיעונים וקשתות בלי ציון. רק השופט קובע את ציון הבסיס τ של כל טיעון
 * ואת הקטגוריה שלו (score). לקשתות אין משקל: ההשפעה של טיעון על היעדים שלו נקבעת על ידי העוצמה הסופית
 * שלו σ, שהסמנטיקה מחשבת.
 */
public class QbafGraph {
    /** מזהה הטענה המרכזית. */
    public static final String CLAIM = "C";
    /**
     * τ(C): ציון הבסיס של הטענה. 0.5 הוא "הערך הנכון כשאין עמדה מוקדמת בעד או נגד" (QuAD, סעיף 4.3),
     * כך שרק הטיעונים מזיזים אותה.
     */
    public static final double NEUTRAL = 0.5;

    public enum Relation {
        ATTACK("attack"), SUPPORT("support");

        private final String id;

        Relation(String id) {
            this.id = id;
        }

        @JsonValue
        public String id() {
            return id;
        }
    }

    /**
     * קודקוד בגרף. side: applicant / government, או null לטענה המרכזית.
     * baseScore (τ) ו-category הם null עד שהשופט מעריך את הטיעון.
     */
    public record Argument(String id, String text, String side, ArgumentCategory category, Double baseScore) {
        public boolean isClaim() {
            return CLAIM.equals(id);
        }
    }

    /** קשת מכוונת מטיעון אל יעד: (from, to) ∈ R⁻ או R⁺. */
    public record Edge(String from, String to, Relation relation) {
    }

    private Map<String, Argument> arguments = new LinkedHashMap<>();
    private List<Edge> edges = new ArrayList<>();

    public QbafGraph(String claimText) {
        arguments.put(CLAIM, new Argument(CLAIM, claimText, null, null, NEUTRAL));
    }

    /**
     * טיעון חדש עם קשת אל כל אחד מהיעדים (לפחות אחד). כל יעד חייב להיות כבר בגרף.
     * targets: יעד → היחס אליו, לפי סדר.
     */
    public void addArgument(String id, String text, String side, Map<String, Relation> targets) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (arguments.containsKey(id)) {
            throw new IllegalArgumentException("Node already exists: " + id);
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("Argument " + id + " must respond to at least one node");
        }
        for (Map.Entry<String, Relation> target : targets.entrySet()) {
            if (!arguments.containsKey(target.getKey())) {
                throw new IllegalArgumentException("Unknown target for " + id + ": " + target.getKey());
            }
            if (target.getValue() == null) {
                throw new IllegalArgumentException("relation must not be null");
            }
        }
        arguments.put(id, new Argument(id, text, side, null, null));
        targets.forEach((target, relation) -> edges.add(new Edge(id, target, relation)));
    }

    /** טיעון חדש עם יעד אחד. */
    public void addArgument(String id, String text, String side, String target, Relation relation) {
        if (relation == null) {
            throw new IllegalArgumentException("relation must not be null");
        }
        Map<String, Relation> targets = new LinkedHashMap<>();
        targets.put(target, relation);
        addArgument(id, text, side, targets);
    }

    /**
     * האם אפשר להוסיף קשת מטיעון קיים אל קודקוד קיים: לא מהטענה המרכזית, לא אל עצמו,
     * לא כפולה, ולא כזאת שסוגרת מעגל.
     */
    public boolean canAddEdge(String from, String to) {
        if (!arguments.containsKey(from) || !arguments.containsKey(to) || CLAIM.equals(from) || from.equals(to)) {
            return false;
        }
        for (Edge edge : edges) {
            if (edge.from().equals(from) && edge.to().equals(to)) {
                return false;
            }
        }
        return !reaches(to, from);
    }

    /** קשת נוספת מטיעון קיים (שימוש חוזר באותו טיעון מול קודקוד נוסף). */
    public void addEdge(String from, String to, Relation relation) {
        if (relation == null) {
            throw new IllegalArgumentException("relation must not be null");
        }
        if (!canAddEdge(from, to)) {
            throw new IllegalArgumentException("Cannot add an edge from " + from + " to " + to
                    + " (unknown node, duplicate edge, or it would close a cycle)");
        }
        edges.add(new Edge(from, to, relation));
    }

    /** האם יש מסלול מכוון from →* to (כולל from == to). */
    public boolean reaches(String from, String to) {
        Deque<String> stack = new ArrayDeque<>(List.of(from));
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (current.equals(to)) {
                return true;
            }
            if (seen.add(current)) {
                for (Edge edge : edges) {
                    if (edge.from().equals(current)) {
                        stack.push(edge.to());
                    }
                }
            }
        }
        return false;
    }

    /** הערכת השופט: קטגוריה וציון בסיס τ ∈ [0, 1]. לטענה המרכזית יש τ קבוע. */
    public void score(String id, ArgumentCategory category, double baseScore) {
        Argument argument = argument(id);
        if (argument.isClaim()) {
            throw new IllegalArgumentException("The claim has a fixed neutral base score");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (!(baseScore >= 0.0 && baseScore <= 1.0)) {
            throw new IllegalArgumentException("base score must be in [0, 1], got " + baseScore);
        }
        arguments.put(id, new Argument(id, argument.text(), argument.side(), category, baseScore));
    }

    /**
     * מיזוג טיעון כפול אל טיעון מוקדם יותר של אותו צד שטוען בדיוק אותו דבר, כדי שאותה טענה לא תיספר פעמיים
     * (ב-QuAD אותו טיעון הוא קודקוד אחד עם כמה קשתות, לא כמה קודקודים).
     * <p>
     * הקשתות של הכפיל עוברות אל המקור: מי שתקף או תמך בכפיל תוקף או תומך עכשיו במקור, והיעדים של הכפיל
     * נעשים יעדים של המקור. קשת שהייתה יוצרת לולאה עצמית, כפילות או מעגל מושמטת. הכפיל נמחק מהגרף.
     * <p>
     * המיזוג נדחה (Optional.empty, והגרף לא משתנה) אם: אחד מהם לא קיים או שהוא הטענה המרכזית; הם אותו קודקוד;
     * הצדדים שונים; המקור לא קודם לכפיל; או שטיעון כלשהו היה נשאר בלי קשת יוצאת.
     *
     * @return הקשתות שנוספו בעקבות המיזוג
     */
    public Optional<List<Edge>> merge(String duplicate, String original) {
        if (!arguments.containsKey(duplicate) || !arguments.containsKey(original)
                || duplicate.equals(original) || CLAIM.equals(duplicate) || CLAIM.equals(original)) {
            return Optional.empty();
        }
        Argument dup = arguments.get(duplicate);
        Argument orig = arguments.get(original);
        if (dup.side() == null || !dup.side().equals(orig.side()) || position(original) > position(duplicate)) {
            return Optional.empty();
        }

        QbafGraph merged = copy();
        List<Edge> incoming = new ArrayList<>();
        List<Edge> outgoing = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.to().equals(duplicate)) {
                incoming.add(edge);
            } else if (edge.from().equals(duplicate)) {
                outgoing.add(edge);
            }
        }
        merged.arguments.remove(duplicate);
        merged.edges.removeIf(e -> e.from().equals(duplicate) || e.to().equals(duplicate));

        List<Edge> added = new ArrayList<>();
        for (Edge edge : incoming) {
            if (merged.canAddEdge(edge.from(), original)) {
                Edge moved = new Edge(edge.from(), original, edge.relation());
                merged.edges.add(moved);
                added.add(moved);
            }
        }
        for (Edge edge : outgoing) {
            if (merged.canAddEdge(original, edge.to())) {
                Edge moved = new Edge(original, edge.to(), edge.relation());
                merged.edges.add(moved);
                added.add(moved);
            }
        }
        for (Argument argument : merged.arguments.values()) {
            if (!argument.isClaim() && merged.edgesFrom(argument.id()).isEmpty()) {
                return Optional.empty();
            }
        }
        this.arguments = merged.arguments;
        this.edges = merged.edges;
        return Optional.of(added);
    }

    public Argument argument(String id) {
        Argument argument = arguments.get(id);
        if (argument == null) {
            throw new IllegalArgumentException("Unknown argument: " + id);
        }
        return argument;
    }

    public boolean contains(String id) {
        return id != null && arguments.containsKey(id);
    }

    public Argument claim() {
        return arguments.get(CLAIM);
    }

    /** כל הקודקודים, הטענה המרכזית ראשונה ואז הטיעונים לפי סדר ההוספה. */
    public Collection<Argument> arguments() {
        return Collections.unmodifiableCollection(arguments.values());
    }

    /** הטיעונים של הצדדים, בלי הטענה המרכזית. */
    public List<Argument> partyArguments() {
        return arguments.values().stream().filter(a -> !a.isClaim()).toList();
    }

    /** המקום של קודקוד בסדר ההוספה (C הוא 0). */
    public int position(String id) {
        int index = 0;
        for (String key : arguments.keySet()) {
            if (key.equals(id)) {
                return index;
            }
            index++;
        }
        throw new IllegalArgumentException("Unknown argument: " + id);
    }

    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /** הקשתות היוצאות מטיעון, לפי סדר ההוספה (לטענה המרכזית אין). */
    public List<Edge> edgesFrom(String id) {
        argument(id);
        return edges.stream().filter(e -> e.from().equals(id)).toList();
    }

    /** R⁻(α): התוקפים הישירים. */
    public List<String> attackers(String id) {
        return incoming(id, Relation.ATTACK);
    }

    /** R⁺(α): התומכים הישירים. */
    public List<String> supporters(String id) {
        return incoming(id, Relation.SUPPORT);
    }

    /** אורך המסלול הקצר ביותר אל הטענה המרכזית: 0 ל-C, 1 למי שמצביע עליה, וכן הלאה. */
    public int depth(String id) {
        argument(id);
        Map<String, Integer> depth = new LinkedHashMap<>();
        depth.put(id, 0);
        Deque<String> queue = new ArrayDeque<>(List.of(id));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (CLAIM.equals(current)) {
                return depth.get(current);
            }
            for (Edge edge : edges) {
                if (edge.from().equals(current) && !depth.containsKey(edge.to())) {
                    depth.put(edge.to(), depth.get(current) + 1);
                    queue.add(edge.to());
                }
            }
        }
        throw new IllegalStateException(id + " does not reach the claim");
    }

    /**
     * הגרף כפי שהשופט רואה אותו כרגע: רק הקודקודים שכבר יש להם ציון בסיס (ו-C), והקשתות שביניהם.
     * ממנו מחושבת ההכרעה באמצע הדיון, כשחלק מהטיעונים עוד מחכים להערכה.
     */
    public QbafGraph scoredSubgraph() {
        QbafGraph sub = new QbafGraph(claim().text());
        for (Argument argument : arguments.values()) {
            if (!argument.isClaim() && argument.baseScore() != null) {
                sub.arguments.put(argument.id(), argument);
            }
        }
        for (Edge edge : edges) {
            if (sub.arguments.containsKey(edge.from()) && sub.arguments.containsKey(edge.to())) {
                sub.edges.add(edge);
            }
        }
        return sub;
    }

    public QbafGraph copy() {
        QbafGraph copy = new QbafGraph(claim().text());
        copy.arguments = new LinkedHashMap<>(arguments);
        copy.edges = new ArrayList<>(edges);
        return copy;
    }

    private List<String> incoming(String id, Relation relation) {
        argument(id);
        List<String> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.to().equals(id) && edge.relation() == relation) {
                result.add(edge.from());
            }
        }
        return result;
    }
}
