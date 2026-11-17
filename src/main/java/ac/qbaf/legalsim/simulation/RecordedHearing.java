package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.PartyAgent;
import ac.qbaf.legalsim.agents.PartyAgent.Submission;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafGraph.Edge;
import ac.qbaf.legalsim.qbaf.QbafSemantics;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Alternative;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Ruling;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * מצב א' כדיון שאפשר להציג: הטענות האמיתיות מפסק הדין, לסירוגין (A1, G1, A2, G2...), סבב אחרי סבב.
 * <p>
 * כל טענה נכנסת לגרף על ידי הסוכן של הצד שלה, עם קשת אל כל קודקוד שהיא עונה לו. המבנה
 * (structure: מזהה → יעדים) נקבע מראש על ידי הסוכנים (PartyAgent.linkSubmissions) ונשמר; בלי מבנה כל טענה
 * מצביעה ישירות על C. אחרי כל טענה השופט מחיל את ההערכה השמורה שלו: טענה שהוא סימן ככפילות של טענה קודמת
 * ממוזגת אליה, ולכל טענה אחרת נקבע ציון הבסיס. ההכרעה מחושבת מחדש על הגרף אחרי כל צעד.
 * <p>
 * האירועים כאן משותפים גם לסימולציה (TrialSimulation), כך שהפרונטאנד מנגן את שניהם באותה דרך.
 */
public final class RecordedHearing {
    /** סוכן של צד הכניס טיעון חדש לגרף, עם הקשתות שלו ובלי ציון. */
    public record Presented(String type, int round, String agent, String side, String argumentId, String text,
                            List<Edge> edges) {
    }

    /**
     * סוכן כיוון טיעון קיים שלו אל יעדים נוספים (שימוש חוזר). הטיעון כבר נשקל, ולכן ההכרעה מתעדכנת מיד.
     */
    public record Linked(String type, int round, String agent, String side, String argumentId, List<Edge> edges,
                         Map<String, Double> strengths, double claimStrength, String leading) {
    }

    /**
     * השופט קבע שהטיעון חוזר בדיוק על duplicateOf, והוא מוזג אליו. edges: הקשתות שעברו אל המקור.
     */
    public record Pruned(String type, int round, String argumentId, String duplicateOf, String rationale,
                         List<Edge> edges, Map<String, Double> strengths, double claimStrength, String leading) {
    }

    /**
     * השופט קבע את ציון הבסיס של הטיעון. strength: הציון שהשופט נתן; baseScore: τ אחרי θ.
     * strengths, claimStrength ו-leading הם ההכרעה על הטיעונים שנשקלו עד עכשיו.
     */
    public record Weighed(String type, int round, String argumentId, String category, double strength,
                          double baseScore, String rationale, Map<String, Double> strengths, double claimStrength,
                          String leading) {
    }

    /**
     * סוף הדיון: ההכרעה מול פסק הדין האמיתי. semantics: הסמנטיקה שלפיה הוכרע.
     * alternatives: σ(C) וההכרעה תחת כל אחת מהסמנטיקות שבמאמר.
     */
    public record Verdict(String type, double claimStrength, Map<String, Double> strengths, String predicted,
                          String actual, String semantics, Map<String, Alternative> alternatives) {
    }

    /** תוצאת הבנייה: הגרף הסופי (עם ציונים), האירועים, והטענות שמוזגו (כפיל → מקור). */
    public record Built(QbafGraph graph, List<Object> events, Map<String, String> pruned) {
    }

    /** הטענות האמיתיות בסדר שבו הן נכנסות לגרף: לסירוגין, המבקש קודם. */
    public static List<Submission> order(LegalCase legalCase) {
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null);
        DefenseAgent defense = new DefenseAgent(m -> null);
        List<String> applicant = legalCase.applicantSubmissions();
        List<String> government = legalCase.governmentSubmissions();
        List<Submission> order = new ArrayList<>();
        for (int i = 0; i < Math.max(applicant.size(), government.size()); i++) {
            if (i < applicant.size()) {
                order.add(new Submission(prosecutor.argumentId(i), prosecutor.side(), applicant.get(i)));
            }
            if (i < government.size()) {
                order.add(new Submission(defense.argumentId(i), defense.side(), government.get(i)));
            }
        }
        return order;
    }

    /**
     * המבנה בלי מעגלים. בפסק הדין כל צד הגיש את הטענות שלו כמכלול, ולכן טענה יכולה לענות לכל טענה של הצד
     * השני, גם כזאת שמופיעה אחריה בסדר — וכך שתי טענות יכולות לענות זו לזו. הקשתות נבדקות לפי הסדר הבסיסי;
     * קשת שהייתה סוגרת מעגל מושמטת, וטענה שלא נשארה לה אף קשת עוברת ל-C. structure == null: null.
     */
    public static Map<String, List<String>> acyclic(List<Submission> base, Map<String, List<String>> structure) {
        if (structure == null) {
            return null;
        }
        Set<String> ids = new LinkedHashSet<>();
        base.forEach(s -> ids.add(s.id()));
        Map<String, List<String>> accepted = new LinkedHashMap<>();
        for (Submission s : base) {
            List<String> kept = new ArrayList<>();
            for (String target : structure.getOrDefault(s.id(), List.of())) {
                boolean valid = QbafGraph.CLAIM.equals(target)
                        || (ids.contains(target) && !target.equals(s.id()) && !reaches(accepted, target, s.id()));
                if (valid && !kept.contains(target)) {
                    kept.add(target);
                }
            }
            accepted.put(s.id(), kept.isEmpty() ? List.of(QbafGraph.CLAIM) : List.copyOf(kept));
        }
        return accepted;
    }

    /**
     * הסדר שבו הטענות נכנסות לגרף לפי המבנה: טענה נכנסת רק אחרי כל הקודקודים שהיא עונה להם. מבין הטענות
     * המוכנות נבחרת הראשונה בסדר הבסיסי, עם העדפה לצד שלא דיבר אחרון — כך הנקודות העיקריות של שני הצדדים
     * באות קודם, לסירוגין, והתגובות אחריהן. structure הוא מבנה חסר מעגלים (acyclic). כשכל קישור הוא לטענה
     * קודמת, הסדר זהה לסדר הבסיסי.
     */
    public static List<Submission> order(LegalCase legalCase, Map<String, List<String>> structure) {
        List<Submission> base = order(legalCase);
        if (structure == null) {
            return base;
        }
        List<Submission> remaining = new ArrayList<>(base);
        List<Submission> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>(Set.of(QbafGraph.CLAIM));
        String lastSide = null;
        while (!remaining.isEmpty()) {
            Submission next = null;
            for (Submission s : remaining) {
                if (placed.containsAll(structure.getOrDefault(s.id(), List.of(QbafGraph.CLAIM)))) {
                    if (next == null) {
                        next = s;
                    }
                    if (!s.side().equals(lastSide)) {
                        next = s;
                        break;
                    }
                }
            }
            if (next == null) {
                throw new IllegalStateException("The structure has a cycle; pass it through acyclic() first");
            }
            remaining.remove(next);
            ordered.add(next);
            placed.add(next.id());
            lastSide = next.side();
        }
        return ordered;
    }

    private static boolean reaches(Map<String, List<String>> edges, String from, String to) {
        Deque<String> stack = new ArrayDeque<>(List.of(from));
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (current.equals(to)) {
                return true;
            }
            if (seen.add(current)) {
                stack.addAll(edges.getOrDefault(current, List.of()));
            }
        }
        return false;
    }

    /**
     * הגרף של כל הטענות האמיתיות לפי המבנה, בלי ציונים ובלי מיזוגים — כפי שהשופט מקבל אותו להערכה.
     * structure == null: הכול אל C.
     */
    public static QbafGraph graph(LegalCase legalCase, Map<String, List<String>> raw) {
        Map<String, List<String>> structure = acyclic(order(legalCase), raw);
        QbafGraph graph = LegalCase.emptyGraph();
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null);
        DefenseAgent defense = new DefenseAgent(m -> null);
        for (Submission s : order(legalCase, structure)) {
            PartyAgent agent = s.side().equals(prosecutor.side()) ? prosecutor : defense;
            agent.present(graph, s.id(), s.text(), targets(graph, structure, s.id(), Map.of()));
        }
        return graph;
    }

    /** בונה את הדיון: גרף, אירועים ומיזוגים, לפי ההערכה השמורה (θ = 1). */
    public static Built build(LegalCase legalCase, List<ScoredArgument> assessment,
                              Map<String, List<String>> raw) {
        Map<String, List<String>> structure = acyclic(order(legalCase), raw);
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> null);
        DefenseAgent defense = new DefenseAgent(m -> null);
        JudgeAgent judge = offlineJudge();
        Map<String, ScoredArgument> byId = new HashMap<>();
        assessment.forEach(s -> byId.put(s.argument().id(), s));

        QbafGraph graph = LegalCase.emptyGraph();
        List<Object> events = new ArrayList<>();
        Map<String, String> pruned = new LinkedHashMap<>();
        int round = 0;
        for (Submission s : order(legalCase, structure)) {
            PartyAgent agent = s.side().equals(prosecutor.side()) ? prosecutor : defense;
            if (agent == prosecutor || round == 0) {
                round++;
            }
            List<Edge> edges = agent.present(graph, s.id(), s.text(), targets(graph, structure, s.id(), pruned));
            events.add(new Presented("present", round, agent.role(), agent.side(), s.id(), s.text(), edges));

            ScoredArgument scored = byId.get(s.id());
            if (scored == null) {
                throw new IllegalStateException("No assessment for " + s.id());
            }
            String original = resolve(scored.duplicateOf(), pruned);
            Optional<List<Edge>> moved = original == null ? Optional.empty() : graph.merge(s.id(), original);
            if (moved.isPresent()) {
                pruned.put(s.id(), original);
                Ruling ruling = judge.decide(graph.scoredSubgraph());
                events.add(new Pruned("prune", round, s.id(), original, scored.rationale(), moved.get(),
                        ruling.strengths(), ruling.claimStrength(), ruling.leading()));
            } else {
                judge.score(graph, scored, Map.of());
                Ruling ruling = judge.decide(graph.scoredSubgraph());
                events.add(new Weighed("weigh", round, s.id(), scored.category().id(), scored.strength(),
                        graph.argument(s.id()).baseScore(), scored.rationale(), ruling.strengths(),
                        ruling.claimStrength(), ruling.leading()));
            }
        }
        if (!graph.partyArguments().isEmpty()) {
            events.add(verdict(judge, graph, legalCase));
        }
        return new Built(graph, events, pruned);
    }

    public static List<Object> replay(LegalCase legalCase, List<ScoredArgument> assessment,
                                      Map<String, List<String>> structure) {
        return build(legalCase, assessment, structure).events();
    }

    public static List<Object> replay(LegalCase legalCase, List<ScoredArgument> assessment) {
        return replay(legalCase, assessment, null);
    }

    /** הגרף של הטענות האמיתיות עם ציוני הבסיס ואחרי המיזוגים. */
    public static QbafGraph weighedGraph(LegalCase legalCase, List<ScoredArgument> assessment,
                                         Map<String, List<String>> structure) {
        return build(legalCase, assessment, structure).graph();
    }

    static Verdict verdict(JudgeAgent judge, QbafGraph graph, LegalCase legalCase) {
        Ruling ruling = judge.decide(graph);
        return new Verdict("verdict", ruling.claimStrength(), ruling.strengths(), ruling.leading(),
                legalCase.outcome(), judge.semanticsName(), QbafSemantics.alternatives(graph));
    }

    /** היעדים מהמבנה שכבר נמצאים בגרף (יעד שמוזג מוחלף במקור שלו); בלי אף יעד תקין: C. */
    private static List<String> targets(QbafGraph graph, Map<String, List<String>> structure, String id,
                                        Map<String, String> pruned) {
        Set<String> targets = new LinkedHashSet<>();
        List<String> wanted = structure == null ? null : structure.get(id);
        if (wanted != null) {
            for (String target : wanted) {
                String resolved = resolve(target, pruned);
                if (graph.contains(resolved)) {
                    targets.add(resolved);
                }
            }
        }
        if (targets.isEmpty()) {
            targets.add(QbafGraph.CLAIM);
        }
        return List.copyOf(targets);
    }

    /** עוקב אחרי שרשרת המיזוגים: אם id מוזג אל X ו-X אל Y, מחזיר Y. */
    static String resolve(String id, Map<String, String> pruned) {
        String current = id;
        while (current != null && pruned.containsKey(current)) {
            current = pruned.get(current);
        }
        return current;
    }

    private static JudgeAgent offlineJudge() {
        return new JudgeAgent(message -> {
            throw new IllegalStateException("The replay uses the judge's saved assessment");
        });
    }

    private RecordedHearing() {
    }
}
