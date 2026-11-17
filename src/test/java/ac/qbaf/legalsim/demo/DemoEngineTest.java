package ac.qbaf.legalsim.demo;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.simulation.RecordedHearing.Presented;
import ac.qbaf.legalsim.simulation.RecordedHearing.Verdict;
import ac.qbaf.legalsim.simulation.RecordedHearing.Weighed;
import ac.qbaf.legalsim.simulation.TrialSimulation.Result;
import ac.qbaf.legalsim.web.CaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoEngineTest {
    private static final LegalCase CASE = new LegalCase("001-77", "X v. Y", "facts",
            List.of("a1", "a2", "a3", "a4", "a5"), List.of("g1", "g2", "g3", "g4"), true, LegalCase.VIOLATION);

    @Test
    void demoSimulationBuildsChainsWithPlaceholderTextAndScores() {
        DemoEngine engine = new DemoEngine(CASE.id());
        Result result = engine.simulation().run(CASE, 3, 3, engine.recordedGraph(CASE));

        assertFalse(result.transcript().isEmpty());
        result.transcript().forEach(t -> assertTrue(t.text().startsWith(DemoEngine.MARK)));
        assertTrue(result.comparison().simulated().maxDepth() >= 2, "expected at least one rebuttal chain");
        assertTrue(result.comparison().simulated().multiTarget() >= 1, "expected an argument with several targets");
        assertTrue(result.comparison().simulated().attacks() > 0);
        result.assessment().forEach(s -> assertTrue(s.rationale().startsWith(DemoEngine.MARK)));
        // הציונים צריכים להיות מגוונים, לא אותו מספר לכולם
        assertTrue(result.assessment().stream().map(s -> s.strength()).distinct().count() >= 4);
        assertTrue(result.events().get(result.events().size() - 1) instanceof Verdict);
        // השוואה מול גרף הטענות האמיתיות של ההדגמה
        int recorded = result.comparison().recorded().arguments().values().stream().mapToInt(i -> i).sum();
        assertTrue(recorded >= 5 && recorded <= 9, "9 real submissions, minus any merged duplicates");
    }

    @Test
    void sameCaseGivesTheSameDemo() {
        Result a = new DemoEngine(CASE.id()).simulation().run(CASE, 3);
        Result b = new DemoEngine(CASE.id()).simulation().run(CASE, 3);

        assertEquals(a.transcript(), b.transcript());
        assertEquals(a.claimStrength(), b.claimStrength(), 1e-12);
    }

    @Test
    void demoHearingUsesTheRealSubmissionsWithRandomStructure() {
        List<Object> events = new DemoEngine(CASE.id()).hearing(CASE);

        List<Presented> presented = events.stream().filter(e -> e instanceof Presented).map(e -> (Presented) e).toList();
        assertEquals(9, presented.size());
        assertEquals("a1", presented.get(0).text());
        assertTrue(presented.stream().anyMatch(p -> p.edges().stream().anyMatch(e -> !"C".equals(e.to()))),
                "expected some rebuttals");
        events.stream().filter(e -> e instanceof Weighed)
                .forEach(e -> assertTrue(((Weighed) e).rationale().startsWith(DemoEngine.MARK)));
    }

    @Test
    void acrossCasesTheDemoShowsEveryOptionOpenToTheAgents() {
        boolean multiTarget = false, reuse = false, pruned = false;
        for (int i = 0; i < 40; i++) {
            DemoEngine engine = new DemoEngine("001-" + i);
            Result result = engine.simulation().run(CASE, 3, 3, engine.recordedGraph(CASE));
            multiTarget |= result.comparison().simulated().multiTarget() > 0;
            reuse |= result.transcript().stream().anyMatch(t -> t.reuse());
            pruned |= !result.pruned().isEmpty();
        }
        assertTrue(multiTarget, "an argument answering several nodes");
        assertTrue(reuse, "an agent reusing an earlier argument");
        assertTrue(pruned, "the judge merging a duplicate");
    }

    @Test
    void serviceDemoNeitherNeedsAModelNorWritesFiles(@TempDir Path data) throws Exception {
        Path cases = Files.createDirectories(data.resolve("cases"));
        new ObjectMapper().writeValue(cases.resolve("001-77.json").toFile(), CASE);
        CaseService service = new CaseService(data, data.resolve("results"), () -> {
            throw new AssertionError("the demo must not create the real simulation");
        });

        Result result = service.simulate("001-77", 3, false, true);
        List<Object> hearing = service.hearing("001-77", true);

        assertFalse(result.transcript().isEmpty());
        assertFalse(hearing.isEmpty());
        assertFalse(Files.exists(data.resolve("results")));
        try (Stream<Path> files = Files.walk(data)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }
}
