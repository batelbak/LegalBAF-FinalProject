package ac.qbaf.legalsim.report;

import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** כותב את הגרף של תיק כ-JSON וכדף HTML שאפשר לפתוח בדפדפן. */
public final class GraphReport {
    public record ArgumentView(String id, String side, String text, String category, double strength,
                               double weight, String supports, String rationale) {
    }

    public record Report(String caseId, String title, String actualOutcome, String predictedOutcome,
                         Map<String, Double> scores, List<ArgumentView> arguments) {
    }

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static Report build(LegalCase legalCase, List<ScoredArgument> scored, QbafGraph graph,
                               Map<String, Double> scores, String predicted) {
        List<ArgumentView> views = scored.stream().map(s -> {
            QbafGraph.Support support = graph.supports().stream()
                    .filter(e -> e.argument().equals(s.argument().id()))
                    .findFirst()
                    .orElseThrow();
            return new ArgumentView(s.argument().id(), s.argument().side(), s.argument().text(),
                    s.category().id(), s.strength(), support.weight(), support.decision(), s.rationale());
        }).toList();
        return new Report(legalCase.id(), legalCase.title(), legalCase.outcome(), predicted, scores, views);
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
