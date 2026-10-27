package ac.qbaf.legalsim.web;

import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.data.CaseLoader;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.simulation.LitigationStrategy;
import ac.qbaf.legalsim.simulation.RecordedHearing;
import ac.qbaf.legalsim.simulation.TippingPointAnalyzer;
import ac.qbaf.legalsim.simulation.TrialSimulation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * הלוגיקה שמאחורי ה-API. קוראת רק מ-data (תיקים והערכות שמורות) ולא מורידה או מחלצת תיקים.
 * פעולות שעוד לא מומשו (סימולציה, אסטרטגיה, נקודות מפנה) זורקות UnsupportedOperationException,
 * וה-API מחזיר עליהן 501.
 */
public class CaseService {
    public record CaseSummary(String id, String title, String outcome, boolean assessed) {
    }

    public record CaseDetails(String id, String title, String facts, String outcome,
                              int applicantArguments, int governmentArguments, boolean assessed) {
    }

    /** תיק או הערכה שלא נמצאו ב-data. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dataDir;
    private final CaseLoader loader;

    public CaseService(Path dataDir) {
        this.dataDir = dataDir;
        this.loader = new CaseLoader(dataDir,
                id -> {
                    throw new IOException("The web server does not download judgments");
                },
                (id, text) -> {
                    throw new IllegalStateException("The web server does not extract judgments");
                });
    }

    /** התיקים שב-data/cases, התיקים של שלב א' קודם. */
    public List<CaseSummary> list() throws IOException {
        Path dir = dataDir.resolve("cases");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> ids;
        try (Stream<Path> files = Files.list(dir)) {
            ids = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted(Comparator.comparingInt(CaseService::sampleOrder).thenComparing(id -> id))
                    .toList();
        }
        List<CaseSummary> cases = new ArrayList<>();
        for (String id : ids) {
            LegalCase legalCase = load(id);
            cases.add(new CaseSummary(id, legalCase.title(), legalCase.outcome(), isAssessed(id)));
        }
        return cases;
    }

    public CaseDetails details(String caseId) {
        LegalCase legalCase = load(caseId);
        return new CaseDetails(legalCase.id(), legalCase.title(), legalCase.facts(), legalCase.outcome(),
                legalCase.applicantSubmissions().size(), legalCase.governmentSubmissions().size(),
                isAssessed(caseId));
    }

    /** מצב א' כדיון: הסוכנים מכניסים את הטענות האמיתיות והשופט שוקל לפי ההערכה השמורה שלו. */
    public List<Object> hearing(String caseId) throws IOException {
        LegalCase legalCase = load(caseId);
        Path path = assessmentPath(caseId);
        if (!Files.exists(path)) {
            throw new NotFoundException("The judge has not assessed " + caseId
                    + " yet. Run RealGraphRun " + caseId + " first.");
        }
        List<ScoredArgument> assessment = JSON.readValue(path.toFile(), new TypeReference<>() {
        });
        return RecordedHearing.replay(legalCase, assessment);
    }

    public Map<String, Object> simulate(String caseId, int rounds) {
        return new TrialSimulation().run(asMap(load(caseId)), rounds);
    }

    public List<Map<String, Object>> counterArguments(String caseId, String claim) {
        return new LitigationStrategy().counterarguments(claim, asMap(load(caseId)));
    }

    public Map<String, Object> tippingPoints(String caseId) {
        return new TippingPointAnalyzer().analyze(asMap(load(caseId)), List.of());
    }

    private LegalCase load(String caseId) {
        if (!caseId.matches("[0-9]{3}-[0-9]+") || !Files.exists(dataDir.resolve("cases").resolve(caseId + ".json"))) {
            throw new NotFoundException("Unknown case: " + caseId);
        }
        return loader.load(caseId);
    }

    private boolean isAssessed(String caseId) {
        return Files.exists(assessmentPath(caseId));
    }

    private Path assessmentPath(String caseId) {
        return dataDir.resolve("assessments").resolve(caseId + ".json");
    }

    private static Map<String, Object> asMap(LegalCase legalCase) {
        return JSON.convertValue(legalCase, new TypeReference<>() {
        });
    }

    private static int sampleOrder(String id) {
        int index = CaseLoader.SAMPLE_IDS.indexOf(id);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
