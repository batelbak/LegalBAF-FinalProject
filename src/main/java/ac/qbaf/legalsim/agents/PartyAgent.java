package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.llm.Llm;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafGraph.Edge;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * סוכן של צד בדיון (תביעה / הגנה). הוא זה שמכניס את הטיעונים של הצד שלו לגרף ה-QBAF.
 * <p>
 * מה פתוח להחלטת הסוכן בכל מהלך:
 * <ul>
 *   <li><b>יעדים</b>: כל טיעון עונה לקודקוד אחד <i>או יותר</i> שכבר קיימים — הטענה המרכזית C או טיעונים קודמים
 *   (למשל "הטיעון הזה דוחה גם את D2 וגם את D4").</li>
 *   <li><b>שימוש חוזר</b>: במקום לנסח מחדש טיעון שכבר העלה, הסוכן יכול לכוון טיעון קיים שלו אל קודקודים נוספים.
 *   אותו טיעון נשאר קודקוד אחד עם כמה קשתות, כמו P1 באיור 3 של QuAD.</li>
 * </ul>
 * היחס של כל קשת נגזר מהצדדים ולא נתון לבחירה, כדי שהגרף יישאר עקבי:
 * אל C — המבקש תומך (support), הממשלה תוקפת (attack); אל טיעון של הצד השני — התקפה;
 * אל טיעון של אותו צד — תמיכה. הסוכן לא קובע ציונים: את ציון הבסיס של כל טיעון קובע השופט.
 * <p>
 * שני מצבים:
 * מצב א' — מכניס את הטענות האמיתיות מפסק הדין, אחרי שהסוכן קבע לכל טענה על מה היא עונה (linkSubmissions).
 * סימולציה — מנסח טיעונים חדשים מתוך העובדות והגרף עד עכשיו (argue).
 */
public abstract class PartyAgent extends Agent {
    /** מקסימום פריטים (טיעונים חדשים ושימושים חוזרים) במהלך אחד בסימולציה. */
    public static final int DEFAULT_MAX_ARGUMENTS = 3;

    /**
     * מהלך אחד שנרשם בדיון. edges: הקשתות שנוספו במהלך הזה. reuse: הסוכן כיוון טיעון קיים אל יעדים נוספים.
     * duplicateOf: השופט קבע שהטיעון חוזר על טיעון קודם, והוא מוזג אליו (null אם לא).
     */
    public record Turn(int round, String argumentId, String side, String text, List<Edge> edges, boolean reuse,
                       String duplicateOf) {
    }

    /** פריט במהלך של סוכן: טיעון חדש, או שימוש חוזר בטיעון קיים שלו. */
    public record Draft(
            @JsonPropertyDescription("One self-contained legal argument of one to three sentences. "
                    + "Leave empty when reuse is set.")
            String text,
            @JsonPropertyDescription("The ids of the nodes this argument responds to, at least one: C for the "
                    + "central claim, and/or ids of earlier arguments such as P2 or D1")
            List<String> targets,
            @JsonPropertyDescription("Normally an empty string. To point one of YOUR OWN earlier arguments at "
                    + "further targets instead of restating it, give that argument's id here")
            String reuse) {

        /** טיעון חדש. */
        public Draft(String text, List<String> targets) {
            this(text, targets, null);
        }

        /** טיעון חדש עם יעד אחד. */
        public Draft(String text, String target) {
            this(text, List.of(target), null);
        }

        public boolean isReuse() {
            return reuse != null;
        }
    }

    /** התשובה המובנית של סוכן במהלך אחד. */
    public record Move(
            @JsonPropertyDescription("The items of this turn. No item may repeat an argument already made.")
            List<Draft> arguments) {
    }

    /**
     * מקסימום קודקודים שטענה אמיתית יכולה לענות להם. יותר מזה הוא כמעט תמיד העתקה של רשימת היעדים המותרים
     * ולא קישור אמיתי, ולכן נדחה.
     */
    public static final int MAX_LINK_TARGETS = 2;

    /**
     * קישור טענה אמיתית אל הקודקודים שהיא עונה להם: C לבד (נקודה עיקרית, שכבה 1), או 1–2 טענות קודמות
     * (תגובה, שמשפיעה על C רק דרך הקודקוד שהיא עונה לו). reason לפני targets כדי שהמודל ינמק לפני שהוא בוחר.
     */
    public record Link(
            @JsonPropertyDescription("The id of one of your submissions, exactly as given, e.g. A3")
            String argumentId,
            @JsonPropertyDescription("One short sentence, written before choosing targets: the specific earlier "
                    + "point this submission answers, or 'main point' if it argues the central claim on its own")
            String reason,
            @JsonPropertyDescription("Either [\"C\"] alone for a main point, or the ids of the one or two earlier "
                    + "submissions it directly answers. Never C together with other ids, never a long list")
            List<String> targets) {

        public Link(String argumentId, List<String> targets) {
            this(argumentId, null, targets);
        }
    }

    public record Links(List<Link> links) {
    }

    /** טענה בסדר שבו הוצגה בדיון, לצורך קישור. */
    public record Submission(String id, String side, String text) {
    }

    private final Function<String, Move> llm;
    private final Function<String, Links> linker;

    protected PartyAgent() {
        this.llm = message -> Llm.structured(systemPrompt(), message, Move.class, Llm.CREATIVE);
        this.linker = message -> Llm.structured(linkingPrompt(), message, Links.class, Llm.PRECISE);
    }

    /** llm מזויף לטסטים. בלי linker, linkSubmissions מקשר הכול ישירות אל C. */
    protected PartyAgent(Function<String, Move> llm) {
        this(llm, null);
    }

    protected PartyAgent(Function<String, Move> llm, Function<String, Links> linker) {
        this.llm = llm;
        this.linker = linker;
    }

    /** הצד בפסק הדין: applicant / government. */
    public abstract String side();

    /** ההחלטה שהצד מבקש. */
    public abstract String decision();

    /** קידומת למזהי הטענות האמיתיות של הצד: A1, A2... / G1, G2... */
    protected abstract String idPrefix();

    /** קידומת למזהי הטיעונים שהסוכן מנסח בסימולציה: P1, P2... / D1, D2... */
    protected abstract String simulatedIdPrefix();

    /** הטענות האמיתיות של הצד מפסק הדין. */
    protected abstract List<String> submissionsOf(LegalCase legalCase);

    /** תיאור התפקיד של הצד בתחילת ה-system prompt. */
    protected abstract String brief();

    /** היחס של הצד אל הטענה המרכזית: המבקש תומך, הממשלה תוקפת. */
    protected abstract Relation towardsClaim();

    /** המזהה של הטענה ה-index (מ-0) של הצד: A1, A2... / G1, G2... */
    public String argumentId(int index) {
        return idPrefix() + (index + 1);
    }

    /** המזהה של הטיעון ה-index (מ-0) שהסוכן ניסח בסימולציה: P1, P2... / D1, D2... */
    public String simulatedArgumentId(int index) {
        return simulatedIdPrefix() + (index + 1);
    }

    /** הטענות האמיתיות של הצד מפסק הדין. */
    public List<String> submissions(LegalCase legalCase) {
        return submissionsOf(legalCase);
    }

    /** היחס שנגזר מהצדדים: אל C לפי הצד, אל טיעון של אותו צד תמיכה, אל טיעון של הצד השני התקפה. */
    public Relation relationTo(QbafGraph graph, String target) {
        QbafGraph.Argument node = graph.argument(target);
        if (node.isClaim()) {
            return towardsClaim();
        }
        return side().equals(node.side()) ? Relation.SUPPORT : Relation.ATTACK;
    }

    /** מכניס טיעון חדש לגרף עם קשת אל כל אחד מהיעדים, בלי ציון. מחזיר את הקשתות שנוספו. */
    public List<Edge> present(QbafGraph graph, String argumentId, String text, List<String> targets) {
        Map<String, Relation> relations = new LinkedHashMap<>();
        for (String target : targets) {
            relations.put(target, relationTo(graph, target));
        }
        graph.addArgument(argumentId, text, side(), relations);
        return graph.edgesFrom(argumentId);
    }

    /** טיעון חדש עם יעד אחד. */
    public List<Edge> present(QbafGraph graph, String argumentId, String text, String target) {
        return present(graph, argumentId, text, List.of(target));
    }

    /**
     * שימוש חוזר: מכוון טיעון קיים של הצד אל יעדים נוספים. יעד שהקשת אליו לא חוקית (כבר קיימת, או שהייתה
     * סוגרת מעגל) מדולג. מחזיר את הקשתות שנוספו בפועל.
     */
    public List<Edge> reuse(QbafGraph graph, String argumentId, List<String> targets) {
        if (!graph.contains(argumentId) || !side().equals(graph.argument(argumentId).side())) {
            throw new IllegalArgumentException(argumentId + " is not an argument of the " + side());
        }
        List<Edge> added = new ArrayList<>();
        for (String target : targets) {
            if (graph.canAddEdge(argumentId, target)) {
                Relation relation = relationTo(graph, target);
                graph.addEdge(argumentId, target, relation);
                added.add(new Edge(argumentId, target, relation));
            }
        }
        return added;
    }

    /** מצב א' בלי מבנה: כל הטענות האמיתיות של הצד ישירות אל C. מחזיר את המזהים. */
    public List<String> presentSubmissions(QbafGraph graph, LegalCase legalCase) {
        List<String> submissions = submissionsOf(legalCase);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < submissions.size(); i++) {
            String id = argumentId(i);
            present(graph, id, submissions.get(i), QbafGraph.CLAIM);
            ids.add(id);
        }
        return ids;
    }

    @Override
    public String systemPrompt() {
        return brief() + """

                The hearing is an argumentation graph. At its root is the central claim C. Every argument \
                responds to one or more earlier nodes, given as its targets:
                - target C: the argument speaks directly to the central claim;
                - target an argument of the other side: your argument attacks (rebuts or undermines) it;
                - target one of your own side's arguments: your argument supports (reinforces) it.
                One argument may have several targets when it genuinely answers several nodes, e.g. a single \
                point that rebuts both D2 and D4. Chains form this way: P1 supports C, D1 attacks P1, P2 \
                attacks D1.

                You receive only the facts of the case and the graph so far. Reason only from these facts and \
                from general principles of the Court's Article 10 case-law. Do not rely on any knowledge of how \
                this particular case was actually argued or decided.

                Rules for every turn:
                - Give at most %d items; fewer is better than weak ones.
                - Each new argument is one self-contained claim of one to three sentences.
                - Choose the targets the argument really answers. Answering the other side's strongest \
                arguments directly is usually more effective than adding another point under C.
                - Never repeat or rephrase an argument already made by either side. If one of YOUR earlier \
                arguments already answers a newer node, do not restate it: add an item with reuse set to that \
                argument's id and targets set to the further nodes it answers. An argument cannot answer a node \
                that itself depends on that argument.
                - Do not assign weights or predict the outcome: the judge alone weighs the arguments."""
                .formatted(DEFAULT_MAX_ARGUMENTS);
    }

    private String linkingPrompt() {
        return brief() + """

                The real written submissions of both parties are given, the other side's first and then yours. \
                They will form an argumentation graph whose root is the central claim C. Each side's submissions \
                were written as a whole, so a submission may answer any submission of the other side, wherever it \
                appears in the list. Classify each of YOUR submissions as exactly one of two kinds:
                - A main point: one of the grounds of your side's case, a reason for your side's position on the \
                central claim (for example legality, legitimate aim, the public interest of the speech, or the \
                proportionality of the penalty). Its targets are ["C"] alone.
                - A response: it answers a specific other submission, either rebutting or undermining a point \
                of the other side, or developing or reinforcing another of your own points. Its targets are \
                that one submission, or two when it explicitly answers both. A response never also targets C: it \
                affects the claim through the point it answers.
                The test: if the submission would still be a reason for your side's position even had the point \
                it contradicts never been made, it is a main point, even though it also contradicts that point. \
                Only a submission that makes sense solely as a reply (for example "dictionaries do not list these \
                words as insults", replying to "the words were insulting") is a response. Both sides present \
                their own case, so at least one of your submissions, and usually several, are main points.
                Sharing a topic with another submission is not enough to answer it. Most submissions answer at \
                most one node, and a submission never answers every other one. Two submissions cannot answer \
                each other: link the one that replies, not the one replied to. Only the ids listed for a \
                submission may be used. In reason, name the specific point answered before choosing targets.""";
    }

    /** תיאור הגרף לסוכנים ולשופט: כל קודקוד עם הצד שלו וכל הקשתות שלו. */
    public static String describe(QbafGraph graph) {
        StringBuilder out = new StringBuilder("C (central claim): ").append(graph.claim().text()).append('\n');
        for (QbafGraph.Argument argument : graph.partyArguments()) {
            out.append(argument.id()).append(" (").append(argument.side()).append(") ")
                    .append(describeEdges(graph.edgesFrom(argument.id()))).append(": ")
                    .append(argument.text()).append('\n');
        }
        return out.toString();
    }

    /** "attacks D1, supports C". */
    public static String describeEdges(List<Edge> edges) {
        List<String> parts = new ArrayList<>();
        for (Edge edge : edges) {
            parts.add(edge.relation().id() + "s " + edge.to());
        }
        return String.join(", ", parts);
    }

    /**
     * סימולציה: מנסח את המהלך הבא של הצד מתוך העובדות והגרף עד עכשיו.
     * מחזיר עד maxArguments פריטים תקינים:
     * טיעון חדש — טקסט לא ריק ויעדים קיימים (בלי יעד תקין: C);
     * שימוש חוזר — טיעון קיים של הצד הזה ולפחות יעד אחד שאפשר להוסיף אליו קשת. פריט לא תקין נזרק.
     */
    public List<Draft> argue(String facts, QbafGraph graph, int round, int maxArguments) {
        StringBuilder message = new StringBuilder("<facts>\n").append(facts).append("\n</facts>\n\n<graph>\n");
        message.append(describe(graph));
        if (graph.partyArguments().isEmpty()) {
            message.append("No arguments have been made yet. You open the hearing.\n");
        }
        message.append("</graph>\n\nThis is round ").append(round).append(". You speak for the ").append(side())
                .append(". Give at most ").append(maxArguments).append(" items, each with its targets.");

        Move move = llm.apply(message.toString());
        List<Draft> drafts = move == null ? null : move.arguments();
        if (drafts == null) {
            return List.of();
        }
        List<Draft> valid = new ArrayList<>();
        for (Draft draft : drafts) {
            if (draft == null || valid.size() >= maxArguments) {
                continue;
            }
            Set<String> targets = new LinkedHashSet<>();
            if (draft.targets() != null) {
                for (String target : draft.targets()) {
                    if (target != null && graph.contains(target.strip())) {
                        targets.add(target.strip());
                    }
                }
            }
            String reuse = draft.reuse() == null ? "" : draft.reuse().strip();
            boolean ownArgument = graph.contains(reuse) && side().equals(graph.argument(reuse).side());
            if (ownArgument) {
                targets.removeIf(target -> !graph.canAddEdge(reuse, target));
                if (!targets.isEmpty()) {
                    valid.add(new Draft(graph.argument(reuse).text(), List.copyOf(targets), reuse));
                }
            } else if (draft.text() != null && !draft.text().isBlank()) {
                if (targets.isEmpty()) {
                    targets.add(QbafGraph.CLAIM);
                }
                valid.add(new Draft(draft.text().strip(), List.copyOf(targets), null));
            }
        }
        return valid;
    }

    /**
     * מצב א': קובע לכל טענה אמיתית של הצד על מה היא עונה. order: כל הטענות של שני הצדדים. טענה יכולה לענות
     * על C או על כל טענה אחרת, גם כזאת שבאה אחריה: כל צד כתב את הטענות שלו כמכלול. מעגלים מוסרים ואת סדר
     * הכניסה לגרף קובעים אחר כך (RecordedHearing.acyclic ו-RecordedHearing.order).
     * <p>
     * כל טענה היא נקודה עיקרית (C לבד) או תגובה (1–MAX_LINK_TARGETS טענות קודמות, בלי C). תגובה שמסומנת גם
     * אל C מאבדת את C, כך שהיא משפיעה על ההכרעה רק דרך הקודקוד שהיא עונה לו. תשובה עם יותר מדי יעדים נשלחת
     * למודל פעם נוספת, ואם היא עדיין לא תקינה — C. לכל צד חייבת להיות לפחות נקודה עיקרית אחת: בלי תומכים
     * (או בלי תוקפים) של C הצד לא יכול להזיז את ההכרעה לכיוון שלו. בלי נקודה עיקרית נשאלים שוב, ואם עדיין
     * אין — הטענה הראשונה של הצד עוברת ל-C.
     * מחזיר מזהה → יעדים לטענות של הצד הזה. בלי linker, או בלי אף יעד חוקי, היעד הוא C.
     */
    public Map<String, List<String>> linkSubmissions(String facts, List<Submission> order) {
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        StringBuilder message = new StringBuilder("<facts>\n").append(facts).append("\n</facts>\n\n<submissions>\n")
                .append("C (central claim): ").append(LegalCase.CLAIM_TEXT).append('\n');
        List<String> all = new ArrayList<>(List.of(QbafGraph.CLAIM));
        // כל צד בנפרד, כפי שבפסק הדין: הרשימות נכתבו כל אחת כמכלול
        for (boolean own : List.of(false, true)) {
            for (Submission s : order) {
                if (s.side().equals(side()) == own) {
                    message.append(s.id()).append(" (").append(s.side()).append("): ").append(s.text()).append('\n');
                }
            }
        }
        order.forEach(s -> all.add(s.id()));
        for (Submission s : order) {
            if (s.side().equals(side())) {
                Set<String> targets = new LinkedHashSet<>(all);
                targets.remove(s.id());
                allowed.put(s.id(), targets);
            }
        }
        message.append("</submissions>\n\nYour submissions and the ids each may respond to:\n");
        allowed.forEach((id, targets) -> message.append(id).append(": ")
                .append(String.join(", ", targets)).append('\n'));

        Map<String, List<String>> result = new LinkedHashMap<>();
        allowed.keySet().forEach(id -> result.put(id, List.of(QbafGraph.CLAIM)));
        if (linker == null || allowed.isEmpty()) {
            return result;
        }
        Set<String> rejected = applyLinks(linker.apply(message.toString()), allowed, result);
        if (!rejected.isEmpty()) {
            message.append("\nYour previous answer gave ").append(String.join(", ", rejected))
                    .append(" more than ").append(MAX_LINK_TARGETS).append(" targets, which is not a real link. ")
                    .append("Answer again: each of them is either a main point with targets [\"C\"] alone, or a ")
                    .append("response with the one or two earlier submissions it directly answers.");
            Map<String, List<String>> retried = new LinkedHashMap<>();
            applyLinks(linker.apply(message.toString()), allowed, retried);
            rejected.forEach(id -> {
                if (retried.containsKey(id)) {
                    result.put(id, retried.get(id));
                }
            });
        }
        if (!hasMainPoint(result)) {
            // צד בלי אף נקודה עיקרית לא יכול להזיז את C לכיוון שלו (בלי תומכים σ(C) ≤ τ(C)), ולכן שואלים שוב
            message.append("\nYour answer has no main point, so your side would present no case of its own. ")
                    .append("Answer again for all your submissions: mark as main points (targets [\"C\"] alone) ")
                    .append("those that would still be reasons for your side's position even had the other side ")
                    .append("said nothing, and keep as responses only those that make sense solely as a reply.");
            Map<String, List<String>> retried = new LinkedHashMap<>();
            applyLinks(linker.apply(message.toString()), allowed, retried);
            if (hasMainPoint(retried)) {
                allowed.keySet().forEach(id -> result.put(id, retried.getOrDefault(id, List.of(QbafGraph.CLAIM))));
            }
            if (!hasMainPoint(result)) {
                // אחרון: הטענה הראשונה של הצד, שבפסק הדין היא בדרך כלל עילת התלונה או ההגנה המרכזית
                result.put(allowed.keySet().iterator().next(), List.of(QbafGraph.CLAIM));
            }
        }
        return result;
    }

    private static boolean hasMainPoint(Map<String, List<String>> links) {
        return links.values().stream().anyMatch(targets -> targets.equals(List.of(QbafGraph.CLAIM)));
    }

    /**
     * מכניס ל-result את הקישורים התקינים. יעד שאינו ברשימה המותרת מושמט; C לצד יעדים אחרים מושמט (תגובה
     * לא נוגעת ישירות בטענה). מחזיר את הטענות שקיבלו יותר מ-MAX_LINK_TARGETS יעדים — אלה לא נכנסות.
     */
    private static Set<String> applyLinks(Links links, Map<String, Set<String>> allowed,
                                          Map<String, List<String>> result) {
        Set<String> rejected = new LinkedHashSet<>();
        if (links == null || links.links() == null) {
            return rejected;
        }
        for (Link link : links.links()) {
            if (link == null || !allowed.containsKey(link.argumentId()) || link.targets() == null) {
                continue;
            }
            Set<String> targets = new LinkedHashSet<>();
            for (String target : link.targets()) {
                if (target != null && allowed.get(link.argumentId()).contains(target.strip())) {
                    targets.add(target.strip());
                }
            }
            if (targets.size() > 1) {
                targets.remove(QbafGraph.CLAIM);
            }
            if (targets.size() > MAX_LINK_TARGETS) {
                rejected.add(link.argumentId());
            } else if (!targets.isEmpty()) {
                result.put(link.argumentId(), List.copyOf(targets));
            }
        }
        return rejected;
    }

    /** מתאם לממשק הכללי של Agent: legalCase עם facts. history לא בשימוש — הסוכן רואה את הגרף. */
    @Override
    public Map<String, Object> respond(Map<String, Object> legalCase, List<Map<String, Object>> history) {
        List<Draft> drafts = argue(String.valueOf(legalCase.get("facts")), LegalCase.emptyGraph(), 1,
                DEFAULT_MAX_ARGUMENTS);
        return Map.of("side", side(), "arguments", drafts);
    }
}
