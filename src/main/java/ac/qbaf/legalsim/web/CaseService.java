package ac.qbaf.legalsim.web;

import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.agents.PartyAgent;
import ac.qbaf.legalsim.data.CaseLoader;
import ac.qbaf.legalsim.demo.DemoEngine;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.llm.Llm;
import ac.qbaf.legalsim.simulation.LitigationStrategy;
import ac.qbaf.legalsim.simulation.RecordedHearing;
import ac.qbaf.legalsim.simulation.SimulationStore;
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
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * הלוגיקה שמאחורי ה-API. קוראת רק מ-data (תיקים והערכות שמורות) ולא מורידה או מחלצת תיקים.
 * ההערכות והסימולציות נקראות מתיקיית התוצאות של המודל הנוכחי (Llm.resultsDir); התיקים מ-data/cases.
 * הסימולציה האדברסרית קוראת למודל השפה ושומרת את התוצאה שם.
 * פעולות שעוד לא מומשו (אסטרטגיה, נקודות מפנה) זורקות UnsupportedOperationException,
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
    private final Path resultsDir;
    private final CaseLoader loader;
    private final SimulationStore simulations;
    private final Supplier<TrialSimulation> simulation;

    public CaseService(Path dataDir) {
        this(dataDir, Llm.resultsDir(dataDir), TrialSimulation::new);
    }

    /** simulation: יוצר את הסימולציה. בטסטים זו סימולציה עם סוכנים מזויפים. */
    public CaseService(Path dataDir, Path resultsDir, Supplier<TrialSimulation> simulation) {
        this.dataDir = dataDir;
        this.resultsDir = resultsDir;
        this.simulations = new SimulationStore(resultsDir);
        this.simulation = simulation;
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
        return hearing(caseId, false);
    }

    /** demo: מבנה וציונים מזויפים (DemoEngine), בלי מודל ובלי קבצים. */
    public List<Object> hearing(String caseId, boolean demo) throws IOException {
        LegalCase legalCase = load(caseId);
        if (demo) {
            return new DemoEngine(caseId).hearing(legalCase);
        }
        Path path = assessmentPath(caseId);
        if (!Files.exists(path)) {
            throw new NotFoundException("The judge has not assessed " + caseId
                    + " yet with " + Llm.model() + ". Run run-local.cmd (or RealGraphRun " + caseId + ") first.");
        }
        List<ScoredArgument> assessment = JSON.readValue(path.toFile(), new TypeReference<>() {
        });
        return RecordedHearing.replay(legalCase, assessment, simulations.structure(caseId));
    }

    /**
     * הסימולציה האדברסרית. סימולציה שמורה עם אותו מספר סבבים מוחזרת בלי קריאות ל-API, אלא אם fresh.
     * ריצה חדשה נשמרת ב-data/simulations.
     */
    public TrialSimulation.Result simulate(String caseId, int rounds, boolean fresh) throws IOException {
        return simulate(caseId, rounds, fresh, false);
    }

    /** demo: סוכנים ושופט מזויפים (DemoEngine). התוצאה לא נשמרת ולא נקראת מהשמורות. */
    public TrialSimulation.Result simulate(String caseId, int rounds, boolean fresh, boolean demo) throws IOException {
        LegalCase legalCase = load(caseId);
        if (demo) {
            DemoEngine engine = new DemoEngine(caseId);
            return engine.simulation().run(legalCase, rounds, PartyAgent.DEFAULT_MAX_ARGUMENTS,
                    engine.recordedGraph(legalCase));
        }
        if (!fresh) {
            Optional<TrialSimulation.Result> saved = simulations.load(caseId);
            if (saved.isPresent() && saved.get().rounds() == rounds) {
                return saved.get();
            }
        }
        TrialSimulation.Result result = simulation.get().run(legalCase, rounds, PartyAgent.DEFAULT_MAX_ARGUMENTS,
                simulations.recordedGraph(legalCase));
        simulations.save(result);
        return result;
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
        return resultsDir.resolve("assessments").resolve(caseId + ".json");
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
