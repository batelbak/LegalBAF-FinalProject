package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.report.GraphReport;
import ac.qbaf.legalsim.simulation.TrialSimulation.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * שמירה של תוצאות הסימולציה, כדי לנתח בלי לשלם שוב על קריאות ל-API.
 * התיקייה היא תיקיית התוצאות של המודל (Llm.resultsDir), למשל data או data/local/ollama-gemma4-12b.
 * &lt;results&gt;/simulations/&lt;id&gt;.json — התמליל, האירועים, ציר הזמן, ההערכות וההשוואה.
 * &lt;results&gt;/simulations/graphs/&lt;id&gt;.html — הגרף הסופי, באותה תצוגה של data/graphs.
 */
public final class SimulationStore {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Path resultsDir;

    public SimulationStore(Path resultsDir) {
        this.resultsDir = resultsDir;
    }

    public Path resultPath(String caseId) {
        return resultsDir.resolve("simulations").resolve(caseId + ".json");
    }

    public Optional<Result> load(String caseId) throws IOException {
        Path path = resultPath(caseId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(JSON.readValue(path.toFile(), Result.class));
    }

    /** כותב את התוצאה ואת דף הגרף. מחזיר את נתיב ה-HTML. */
    public Path save(Result result) throws IOException {
        Path path = resultPath(result.caseId());
        Files.createDirectories(path.getParent());
        JSON.writeValue(path.toFile(), result);
        return GraphReport.write(path.getParent().resolve("graphs"), result.graph());
    }

    /** ההערכה השמורה של השופט על הטענות האמיתיות. null אם התיק עוד לא הוערך. */
    public List<ScoredArgument> recordedAssessment(String caseId) throws IOException {
        Path path = assessmentPath(caseId);
        if (!Files.exists(path)) {
            return null;
        }
        return JSON.readValue(path.toFile(), new TypeReference<>() {
        });
    }

    public Path assessmentPath(String caseId) {
        return resultsDir.resolve("assessments").resolve(caseId + ".json");
    }

    public void saveAssessment(String caseId, List<ScoredArgument> assessment) throws IOException {
        Path path = assessmentPath(caseId);
        Files.createDirectories(path.getParent());
        JSON.writeValue(path.toFile(), assessment);
    }

    /**
     * המבנה של הטענות האמיתיות: מזהה טענה → הקודקודים שהיא עונה להם (C ו/או טענות קודמות).
     * null אם הסוכנים עוד לא קישרו את התיק — אז כל טענה מצביעה על C.
     */
    public Map<String, List<String>> structure(String caseId) throws IOException {
        Path path = structurePath(caseId);
        if (!Files.exists(path)) {
            return null;
        }
        return JSON.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, List<String>>>() {
        });
    }

    public void saveStructure(String caseId, Map<String, List<String>> structure) throws IOException {
        Path path = structurePath(caseId);
        Files.createDirectories(path.getParent());
        JSON.writeValue(path.toFile(), structure);
    }

    private Path structurePath(String caseId) {
        return resultsDir.resolve("structures").resolve(caseId + ".json");
    }

    /** גרף הטענות האמיתיות עם ציוני השופט השמורים, להשוואה. null אם אין הערכה. */
    public QbafGraph recordedGraph(LegalCase legalCase) throws IOException {
        List<ScoredArgument> assessment = recordedAssessment(legalCase.id());
        if (assessment == null) {
            return null;
        }
        return RecordedHearing.weighedGraph(legalCase, assessment, structure(legalCase.id()));
    }
}
