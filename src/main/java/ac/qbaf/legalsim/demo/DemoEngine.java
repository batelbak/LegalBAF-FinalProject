package ac.qbaf.legalsim.demo;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.ArgumentScore;
import ac.qbaf.legalsim.agents.JudgeAgent.Assessment;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.PartyAgent;
import ac.qbaf.legalsim.agents.PartyAgent.Draft;
import ac.qbaf.legalsim.agents.PartyAgent.Link;
import ac.qbaf.legalsim.agents.PartyAgent.Links;
import ac.qbaf.legalsim.agents.PartyAgent.Move;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.simulation.RecordedHearing;
import ac.qbaf.legalsim.simulation.TrialSimulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * מצב הדגמה: תוצאות מזויפות במקום מודל שפה, כדי לראות את הגרף, השרשראות, הציונים וההכרעה בלי להריץ מודל.
 * <p>
 * אין כאן שום ניתוח משפטי. הסוכנים "מנסחים" טקסט קבוע שמסומן [DEMO] ומקבלים החלטות פסאודו-אקראיות מכל
 * הסוגים שפתוחים לסוכן אמיתי: יעד אחד או כמה יעדים, שימוש חוזר בטיעון קיים, והשופט נותן ציונים ולפעמים
 * מסמן טיעון ככפילות. כל השאר — הגרף, היחסים, המיזוג, הסמנטיקה וההכרעה — הוא הקוד האמיתי.
 * התוצאות לא נשמרות לשום מקום. הזרע נגזר ממזהה התיק, כך שאותו תיק נותן תמיד אותה הדגמה.
 */
public final class DemoEngine {
    public static final String MARK = "[DEMO]";

    private static final Pattern GRAPH_LINE = Pattern.compile("^([A-Z]+\\d+) \\((applicant|government)\\)",
            Pattern.MULTILINE);
    private static final Pattern ALLOWED_LINE = Pattern.compile("^([A-Z]+\\d+): (.+)$", Pattern.MULTILINE);
    private static final ArgumentCategory[] CATEGORIES = ArgumentCategory.values();

    private final Random random;
    private final String caseId;

    public DemoEngine(String caseId) {
        this.caseId = caseId;
        this.random = new Random(caseId.hashCode());
    }

    /** סימולציה עם סוכנים ושופט מזויפים. */
    public TrialSimulation simulation() {
        return new TrialSimulation(new ProsecutorAgent(party("applicant"), linker()),
                new DefenseAgent(party("government"), linker()), judge());
    }

    /** מצב א' בהדגמה: הטענות האמיתיות, עם מבנה וציונים מזויפים. */
    public List<Object> hearing(LegalCase legalCase) {
        return recorded(legalCase).events();
    }

    /** גרף הטענות האמיתיות עם מבנה וציונים מזויפים, להשוואה בסימולציית ההדגמה. */
    public QbafGraph recordedGraph(LegalCase legalCase) {
        return recorded(legalCase).graph();
    }

    private RecordedHearing.Built recorded(LegalCase legalCase) {
        List<PartyAgent.Submission> order = RecordedHearing.order(legalCase);
        Map<String, List<String>> structure = new LinkedHashMap<>();
        structure.putAll(new ProsecutorAgent(m -> null, linker()).linkSubmissions(legalCase.facts(), order));
        structure.putAll(new DefenseAgent(m -> null, linker()).linkSubmissions(legalCase.facts(), order));
        QbafGraph graph = RecordedHearing.graph(legalCase, structure);
        List<ScoredArgument> assessment = judge().assess(legalCase.facts(), JudgeAgent.argumentsIn(graph), graph);
        return RecordedHearing.build(legalCase, assessment, structure);
    }

    /**
     * סוכן מזויף: 1–3 פריטים. טיעון חדש עונה ל-C, לטיעון של הצד השני או לטיעון של הצד שלו, ולפעמים לשניים;
     * לפעמים הסוכן משתמש שוב בטיעון קיים שלו מול טיעון חדש של הצד השני.
     */
    private Function<String, Move> party(String side) {
        return message -> {
            String graph = section(message, "<graph>", "</graph>");
            List<String> opponents = new ArrayList<>();
            List<String> own = new ArrayList<>();
            Matcher m = GRAPH_LINE.matcher(graph);
            while (m.find()) {
                (m.group(2).equals(side) ? own : opponents).add(m.group(1));
            }
            String who = side.equals("applicant") ? "applicant" : "Government";
            int count = 1 + random.nextInt(3);
            List<Draft> drafts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (own.size() >= 2 && !opponents.isEmpty() && random.nextDouble() < 0.15) {
                    // שימוש חוזר: טיעון ישן של הצד מול אחד הטיעונים האחרונים של הצד השני
                    drafts.add(new Draft("", List.of(recent(opponents)), own.get(random.nextInt(own.size() / 2 + 1))));
                    continue;
                }
                Set<String> targets = new LinkedHashSet<>();
                double roll = random.nextDouble();
                if (!opponents.isEmpty() && roll < 0.6) {
                    targets.add(recent(opponents));
                    if (opponents.size() >= 2 && random.nextDouble() < 0.35) {
                        targets.add(recent(opponents));
                    }
                } else if (!own.isEmpty() && roll < 0.75) {
                    targets.add(own.get(random.nextInt(own.size())));
                } else {
                    targets.add(QbafGraph.CLAIM);
                }
                if (!targets.contains(QbafGraph.CLAIM) && random.nextDouble() < 0.15) {
                    targets.add(QbafGraph.CLAIM);
                }
                drafts.add(new Draft(MARK + " Placeholder " + who + " argument responding to "
                        + String.join(" and ", targets) + ". No model was run, so this text carries no legal content.",
                        List.copyOf(targets)));
            }
            return new Move(drafts);
        };
    }

    /** אחד משלושת הטיעונים האחרונים ברשימה. */
    private String recent(List<String> ids) {
        int from = Math.max(0, ids.size() - 3);
        return ids.get(from + random.nextInt(ids.size() - from));
    }

    /** קישור מזויף של טענות אמיתיות: בערך מחצית עונות לטענה קודמת של הצד השני (לפעמים לשתיים), השאר ל-C. */
    private Function<String, Links> linker() {
        return message -> {
            String allowedPart = message.substring(Math.max(0, message.indexOf("Your submissions and the ids")));
            List<Link> links = new ArrayList<>();
            Matcher m = ALLOWED_LINE.matcher(allowedPart);
            while (m.find()) {
                String id = m.group(1);
                List<String> options = new ArrayList<>();
                for (String option : m.group(2).split(",\\s*")) {
                    // רק טענות של הצד השני (אות ראשונה שונה) — כך מתקבלות התקפות
                    if (!option.equals(QbafGraph.CLAIM) && option.charAt(0) != id.charAt(0)) {
                        options.add(option);
                    }
                }
                Set<String> targets = new LinkedHashSet<>();
                if (!options.isEmpty() && random.nextDouble() < 0.5) {
                    targets.add(recent(options));
                    if (options.size() >= 2 && random.nextDouble() < 0.3) {
                        targets.add(recent(options));
                    }
                } else {
                    targets.add(QbafGraph.CLAIM);
                }
                links.add(new Link(id, List.copyOf(targets)));
            }
            return new Links(links);
        };
    }

    /**
     * שופט מזויף: ציון 0.25–0.9 וקטגוריה, יציבים לכל טיעון בתיק. בערך אחד מעשרה טיעונים מסומן ככפילות של
     * טיעון קודם של אותו צד, כדי שגם המיזוג ייראה בהדגמה.
     */
    private JudgeAgent judge() {
        return new JudgeAgent(message -> {
            // הסדר והצד של כל טיעון בגרף, כדי לדעת מי קודם למי
            List<String[]> known = new ArrayList<>();
            Matcher g = GRAPH_LINE.matcher(section(message, "<graph>", "</graph>"));
            while (g.find()) {
                known.add(new String[]{g.group(1), g.group(2)});
            }
            String toAssess = section(message, "<arguments to assess>", "</arguments to assess>");
            List<ArgumentScore> scores = new ArrayList<>();
            Matcher m = GRAPH_LINE.matcher(toAssess);
            while (m.find()) {
                String id = m.group(1);
                // זרעים קרובים נותנים ב-Random ערך ראשון כמעט זהה, ולכן מערבבים את הזרע ומדלגים על הערך הראשון
                Random r = new Random((caseId + "#" + id).hashCode() * 0x9E3779B97F4A7C15L);
                r.nextLong();
                double strength = Math.round((0.25 + r.nextDouble() * 0.65) * 20) / 20.0;
                ArgumentCategory category = CATEGORIES[r.nextInt(CATEGORIES.length)];
                String duplicateOf = "";
                if (r.nextDouble() < 0.1) {
                    List<String> earlierOwn = new ArrayList<>();
                    for (String[] node : known) {
                        if (node[0].equals(id)) {
                            break;
                        }
                        if (node[1].equals(m.group(2))) {
                            earlierOwn.add(node[0]);
                        }
                    }
                    if (!earlierOwn.isEmpty()) {
                        duplicateOf = earlierOwn.get(r.nextInt(earlierOwn.size()));
                    }
                }
                scores.add(new ArgumentScore(id, category.id(), strength, duplicateOf, duplicateOf.isEmpty()
                        ? MARK + " Placeholder score. No model was run."
                        : MARK + " Placeholder: treated as a restatement of " + duplicateOf + ". No model was run."));
            }
            return new Assessment(scores);
        });
    }

    private static String section(String message, String open, String close) {
        int start = message.indexOf(open);
        int end = message.indexOf(close);
        if (start < 0) {
            return "";
        }
        return message.substring(start, end < 0 ? message.length() : end);
    }
}
