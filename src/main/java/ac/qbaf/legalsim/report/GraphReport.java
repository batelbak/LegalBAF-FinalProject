package ac.qbaf.legalsim.report;

import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafSemantics.Ruling;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** כותב את גרף ה-QBAF של תיק כ-JSON וכדף HTML שאפשר לפתוח בדפדפן. */
public final class GraphReport {
    /**
     * טיעון בגרף. strength: הציון שהשופט נתן; baseScore: τ; finalStrength: σ אחרי DF-QuAD.
     * edges: הקשתות היוצאות (attack / support) אל C ו/או אל טיעונים אחרים. depth: המרחק הקצר ביותר מ-C.
     */
    public record ArgumentView(String id, String side, String text, String category, double strength,
                               double baseScore, double finalStrength, List<QbafGraph.Edge> edges,
                               int depth, String rationale) {
    }

    /** טיעון שהשופט מיזג אל טיעון קודם כי הוא חוזר עליו בדיוק. */
    public record PrunedView(String id, String side, String text, String duplicateOf) {
    }

    public record Report(String caseId, String title, String claim, String actualOutcome, String predictedOutcome,
                         double claimStrength, List<ArgumentView> arguments, List<PrunedView> pruned) {
    }

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static Report build(LegalCase legalCase, List<ScoredArgument> scored, QbafGraph graph, Ruling ruling) {
        Map<String, ScoredArgument> byId = new HashMap<>();
        scored.forEach(s -> byId.put(s.argument().id(), s));
        List<ArgumentView> views = graph.partyArguments().stream().map(a -> {
            ScoredArgument s = byId.get(a.id());
            return new ArgumentView(a.id(), a.side(), a.text(), a.category() == null ? null : a.category().id(),
                    s == null ? 0.0 : s.strength(), a.baseScore() == null ? 0.0 : a.baseScore(),
                    ruling.strengths().getOrDefault(a.id(), 0.0), graph.edgesFrom(a.id()),
                    graph.depth(a.id()), s == null ? null : s.rationale());
        }).toList();
        List<PrunedView> pruned = scored.stream()
                .filter(s -> !graph.contains(s.argument().id()))
                .map(s -> new PrunedView(s.argument().id(), s.argument().side(), s.argument().text(),
                        s.duplicateOf()))
                .toList();
        return new Report(legalCase.id(), legalCase.title(), graph.claim().text(), legalCase.outcome(),
                ruling.leading(), ruling.claimStrength(), views, pruned);
    }

    /** כותב את &lt;dir&gt;/&lt;id&gt;.json ו-&lt;dir&gt;/&lt;id&gt;.html ומחזיר את נתיב ה-HTML. */
    public static Path write(Path dir, Report report) throws IOException {
        Files.createDirectories(dir);
        String json = JSON.writeValueAsString(report);
        Files.writeString(dir.resolve(report.caseId() + ".json"), json, StandardCharsets.UTF_8);

        String template;
        try (InputStream in = GraphReport.class.getResourceAsStream("/graph-view.html")) {
            if (in == null) {
                throw new IOException("Missing resource graph-view.html");
            }
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // "</" בתוך JSON יכול לסגור את תגית ה-script, ולכן מבריחים אותו
        String html = template.replace("/*DATA*/null", json.replace("</", "<\\/"));
        Path htmlPath = dir.resolve(report.caseId() + ".html");
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
        return htmlPath;
    }

    private GraphReport() {
    }
}
