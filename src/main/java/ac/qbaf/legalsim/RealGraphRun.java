package ac.qbaf.legalsim;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.CaseLoader;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.report.GraphReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * מצב א': הטענות האמיתיות מפסק הדין, בלי סימולציה.
 * סוכן התביעה מכניס לגרף את טענות המבקש וסוכן ההגנה את טענות הממשלה. השופט שוקל את הקשתות ומכריע.
 * ההערכה של השופט נשמרת ב-data/assessments כדי לא לשלם עליה שוב.
 * mvn compile exec:java -Dexec.mainClass=ac.qbaf.legalsim.RealGraphRun -Dexec.args=001-164453
 */
public final class RealGraphRun {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws IOException {
        List<String> ids = args.length > 0 ? List.of(args) : CaseLoader.SAMPLE_IDS;
        CaseLoader loader = new CaseLoader();
        ProsecutorAgent prosecutor = new ProsecutorAgent();
        DefenseAgent defense = new DefenseAgent();
        JudgeAgent judge = new JudgeAgent();

        for (String id : ids) {
            LegalCase legalCase = loader.load(id);
            QbafGraph graph = LegalCase.emptyGraph();
            prosecutor.presentSubmissions(graph, legalCase);
            defense.presentSubmissions(graph, legalCase);

            List<ScoredArgument> scored = assessment(judge, legalCase, graph);
            judge.weigh(graph, scored, Map.of());
            Map<String, Object> decision = judge.decide(graph);

            @SuppressWarnings("unchecked")
            Map<String, Double> scores = (Map<String, Double>) decision.get("scores");
            String predicted = (String) decision.get("leading");
            Path html = GraphReport.write(Path.of("data", "graphs"),
                    GraphReport.build(legalCase, scored, graph, scores, predicted));

            System.out.printf("%s  predicted=%s  actual=%s  scores=%s%n  %s%n",
                    id, predicted, legalCase.outcome(), scores, html.toAbsolutePath());
        }
    }

    private static List<ScoredArgument> assessment(JudgeAgent judge, LegalCase legalCase, QbafGraph graph)
            throws IOException {
        Path path = Path.of("data", "assessments", legalCase.id() + ".json");
        if (Files.exists(path)) {
            return JSON.readValue(path.toFile(), new TypeReference<>() {
            });
        }
        List<ScoredArgument> scored = judge.assess(legalCase.facts(), JudgeAgent.argumentsIn(graph));
        Files.createDirectories(path.getParent());
        JSON.writeValue(path.toFile(), scored);
        return scored;
    }

    private RealGraphRun() {
    }
}
