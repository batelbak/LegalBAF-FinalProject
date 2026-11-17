package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.ArgumentScore;
import ac.qbaf.legalsim.agents.JudgeAgent.Assessment;
import ac.qbaf.legalsim.agents.PartyAgent.Draft;
import ac.qbaf.legalsim.agents.PartyAgent.Move;
import ac.qbaf.legalsim.agents.PartyAgent.Turn;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.qbaf.QbafGraph;
import ac.qbaf.legalsim.qbaf.QbafGraph.Edge;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import ac.qbaf.legalsim.simulation.RecordedHearing.Linked;
import ac.qbaf.legalsim.simulation.RecordedHearing.Presented;
import ac.qbaf.legalsim.simulation.RecordedHearing.Pruned;
import ac.qbaf.legalsim.simulation.RecordedHearing.Verdict;
import ac.qbaf.legalsim.simulation.RecordedHearing.Weighed;
import ac.qbaf.legalsim.simulation.TrialSimulation.Result;
import ac.qbaf.legalsim.web.CaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * הדיון המזויף הבסיסי: סבב 1 — P1 ו-P2 תומכים ב-C, D1 תוקף את P1.
 * סבב 2 — P3 תוקף את D1, P4 תומך ב-C, D2 תוקף את C.
 * השופט: טיעון של המבקש τ = 0.4, טיעון של הממשלה τ = 0.9.
 */
class TrialSimulationTest {
    private static final LegalCase CASE = new LegalCase("001-1", "X v. Y", "THE FACTS",
            List.of("REAL APPLICANT SUBMISSION"), List.of("REAL GOVERNMENT SUBMISSION"), true,
            LegalCase.VIOLATION);

    private static final Pattern ARGUMENT_LINE = Pattern.compile("^([PD]\\d+) \\((applicant|government)\\)",
            Pattern.MULTILINE);

    private final List<String> prosecutorMessages = new ArrayList<>();
    private final List<String> defenseMessages = new ArrayList<>();
    private final List<String> judgeMessages = new ArrayList<>();

    private static final List<List<Draft>> PROSECUTION = List.of(
            List.of(new Draft("pros 1", "C"), new Draft("pros 2", "C")),
            List.of(new Draft("pros 3", "D1"), new Draft("pros 4", "C")));
    private static final List<List<Draft>> DEFENCE = List.of(
            List.of(new Draft("def 1", "P1")),
            List.of(new Draft("def 2", "C")));

    private TrialSimulation simulation() {
        return simulation(PROSECUTION, DEFENCE, Map.of());
    }

    /** duplicates: טיעון → הטיעון שהשופט המזויף יגיד שהוא חוזר עליו. */
    private TrialSimulation simulation(List<List<Draft>> prosecution, List<List<Draft>> defence,
                                       Map<String, String> duplicates) {
        AtomicInteger p = new AtomicInteger();
        AtomicInteger d = new AtomicInteger();
        ProsecutorAgent prosecutor = new ProsecutorAgent(message -> {
            prosecutorMessages.add(message);
            return new Move(prosecution.get(p.getAndIncrement() % prosecution.size()));
        });
        DefenseAgent defense = new DefenseAgent(message -> {
            defenseMessages.add(message);
            return new Move(defence.get(d.getAndIncrement() % defence.size()));
        });
        JudgeAgent judge = new JudgeAgent(message -> {
            judgeMessages.add(message);
            String toAssess = message.substring(message.indexOf("<arguments to assess>"));
            List<ArgumentScore> scores = new ArrayList<>();
            Matcher m = ARGUMENT_LINE.matcher(toAssess);
            while (m.find()) {
                boolean applicant = m.group(2).equals("applicant");
                scores.add(new ArgumentScore(m.group(1), applicant ? "public_interest" : "content_and_form",
                        applicant ? 0.4 : 0.9, duplicates.getOrDefault(m.group(1), ""), "r"));
            }
            return new Assessment(scores);
        });
        return new TrialSimulation(prosecutor, defense, judge);
    }

    @Test
    void agentsArgueFromTheFactsOnlyAndSeeTheGraphSoFar() {
        simulation().run(CASE, 2);

        assertEquals(2, prosecutorMessages.size());
        assertEquals(2, defenseMessages.size());
        for (String message : List.of(prosecutorMessages.get(0), defenseMessages.get(1))) {
            assertTrue(message.contains("THE FACTS"));
            assertFalse(message.contains("REAL APPLICANT SUBMISSION"));
            assertFalse(message.contains("REAL GOVERNMENT SUBMISSION"));
        }
        assertTrue(prosecutorMessages.get(0).contains("You open the hearing"));
        // ההגנה רואה את טיעוני התביעה מאותו סבב, עם היחס שלהם
        assertTrue(defenseMessages.get(0).contains("P1 (applicant) supports C: pros 1"));
        // בסבב 2 התביעה רואה את ההתקפה של ההגנה
        assertTrue(prosecutorMessages.get(1).contains("D1 (government) attacks P1: def 1"));
    }

    @Test
    void judgeScoresOnlyTheNewArgumentsAfterEachRound() {
        simulation().run(CASE, 2);

        assertEquals(2, judgeMessages.size());
        String secondRound = judgeMessages.get(1).substring(judgeMessages.get(1).indexOf("<arguments to assess>"));
        assertFalse(secondRound.contains("P1 (applicant)"));
        assertTrue(secondRound.contains("P3 (applicant) attacks D1"));
        assertTrue(secondRound.contains("D2 (government) attacks C"));
    }

    @Test
    void eventsBuildTheGraphAndTheClaimStrengthFollowsDfQuad() {
        Result result = simulation().run(CASE, 2);
        List<Object> events = result.events();

        assertEquals(13, events.size());
        Presented d1 = (Presented) events.get(2);
        assertEquals("D1", d1.argumentId());
        assertEquals(List.of(new Edge("D1", "P1", Relation.ATTACK)), d1.edges());

        // אחרי P1: σ(C) = 0.5 + 0.5·0.4 = 0.7; אחרי P2: 0.82
        assertEquals(0.7, ((Weighed) events.get(3)).claimStrength(), 1e-9);
        assertEquals(0.82, ((Weighed) events.get(4)).claimStrength(), 1e-9);
        // אחרי D1: P1 = 0.4·0.1 = 0.04 → תומכים 0.424 → 0.712
        Weighed afterD1 = (Weighed) events.get(5);
        assertEquals(0.04, afterD1.strengths().get("P1"), 1e-9);
        assertEquals(0.712, afterD1.claimStrength(), 1e-9);

        Presented p3 = (Presented) events.get(6);
        assertEquals(2, p3.round());
        assertEquals(List.of(new Edge("P3", "D1", Relation.ATTACK)), p3.edges());

        // סוף: D1 = 0.9·0.6 = 0.54, P1 = 0.4·0.46 = 0.184, תומכים 0.70624, תוקף 0.9 → 0.40312
        Verdict verdict = (Verdict) events.get(12);
        assertEquals(0.40312, verdict.claimStrength(), 1e-9);
        assertEquals(LegalCase.NO_VIOLATION, verdict.predicted());
        assertEquals(LegalCase.VIOLATION, verdict.actual());
        assertEquals("DF-QuAD", result.semantics());
        assertEquals(0.40312, result.alternatives().get("df-quad").claimStrength(), 1e-9);
        assertTrue(result.alternatives().containsKey("quad") && result.alternatives().containsKey("euler"));
    }

    @Test
    void multiTargetArgumentsReuseAndPruningAllShapeTheGraph() {
        // סבב 1: P1, P2 → C. D1 תוקף גם את P1 וגם את P2; D2 → C.
        // סבב 2: P3 תוקף את D1; התביעה משתמשת שוב ב-P2, הפעם נגד D2. D3 → C, והשופט קובע שהוא חוזר על D2.
        List<List<Draft>> prosecution = List.of(
                List.of(new Draft("pros 1", "C"), new Draft("pros 2", "C")),
                List.of(new Draft("pros 3", "D1"), new Draft("", List.of("D2"), "P2")));
        List<List<Draft>> defence = List.of(
                List.of(new Draft("def 1", List.of("P1", "P2")), new Draft("def 2", "C")),
                List.of(new Draft("def 3", "C")));

        Result result = simulation(prosecution, defence, Map.of("D3", "D2")).run(CASE, 2);
        List<Object> events = result.events();

        // סבב 1: 4 טיעונים + 4 שקילות. סבב 2: P3, שימוש חוזר, D3, שקילת P3, מיזוג D3. ואז ההכרעה
        assertEquals(14, events.size());
        assertEquals(List.of(new Edge("D1", "P1", Relation.ATTACK), new Edge("D1", "P2", Relation.ATTACK)),
                ((Presented) events.get(2)).edges());
        // סוף סבב 1: P1 = P2 = 0.04, תומכים 0.0784, תוקף 0.9 → 0.0892
        assertEquals(0.0892, ((Weighed) events.get(7)).claimStrength(), 1e-9);

        // השימוש החוזר משפיע מיד: D2 = 0.9·0.96 = 0.864 → σ(C) = 0.5 − 0.5·0.7856 = 0.1072
        Linked linked = (Linked) events.get(9);
        assertEquals("P2", linked.argumentId());
        assertEquals(List.of(new Edge("P2", "D2", Relation.ATTACK)), linked.edges());
        assertEquals(0.1072, linked.claimStrength(), 1e-9);

        // אחרי P3: D1 = 0.54, P1 = P2 = 0.184, D2 = 0.7344, תומכים 0.334144 → 0.299872
        assertEquals(0.299872, ((Weighed) events.get(11)).claimStrength(), 1e-9);
        Pruned pruned = (Pruned) events.get(12);
        assertEquals("D3", pruned.argumentId());
        assertEquals("D2", pruned.duplicateOf());
        // D3 לא נספר: ההכרעה לא זזה
        assertEquals(0.299872, pruned.claimStrength(), 1e-9);
        assertEquals(0.299872, result.claimStrength(), 1e-9);

        assertEquals(Map.of("D3", "D2"), result.pruned());
        assertEquals(List.of("D3"), result.graph().pruned().stream().map(p -> p.id()).toList());
        assertEquals(5, result.graph().arguments().size());
        Turn reuse = result.transcript().get(5);
        assertTrue(reuse.reuse());
        assertEquals("P2", reuse.argumentId());
        assertEquals("pros 2", reuse.text());
        assertEquals("D2", result.transcript().get(6).duplicateOf());
        assertEquals(4, result.timeline().get(0).arguments());
        assertEquals(5, result.timeline().get(1).arguments());

        SimulationComparison.Side side = result.comparison().simulated();
        assertEquals(2, side.multiTarget());
        assertEquals(5, side.attacks());
        assertEquals(2, side.supports());
    }

    @Test
    void timelineAndComparisonDescribeTheHearing() {
        QbafGraph recorded = LegalCase.emptyGraph();
        recorded.addArgument("A1", "a", "applicant", QbafGraph.CLAIM, Relation.SUPPORT);
        recorded.score("A1", ArgumentCategory.PUBLIC_INTEREST, 0.7);
        recorded.addArgument("G1", "g", "government", QbafGraph.CLAIM, Relation.ATTACK);
        recorded.score("G1", ArgumentCategory.OTHER, 0.2);

        Result result = simulation().run(CASE, 2, 3, recorded);

        assertEquals(2, result.timeline().size());
        assertEquals(3, result.timeline().get(0).arguments());
        assertEquals(LegalCase.VIOLATION, result.timeline().get(0).leading());
        assertEquals(LegalCase.NO_VIOLATION, result.timeline().get(1).leading());

        SimulationComparison.Comparison c = result.comparison();
        assertFalse(c.simulatedMatchesActual());
        assertEquals(LegalCase.VIOLATION, c.recorded().predicted());
        assertEquals(0.25, c.recorded().margin(), 1e-9);
        assertEquals(0.40312 - 0.5, c.simulated().margin(), 1e-9);
        assertFalse(c.simulatedMatchesRecorded());
        assertEquals(3, c.simulated().attacks());
        assertEquals(3, c.simulated().supports());
        assertEquals(3, c.simulated().maxDepth());
        assertEquals(0, c.simulated().multiTarget());
        assertEquals(1.6, c.simulated().baseScoreByCategory().get("applicant").get("public_interest"), 1e-9);
        assertEquals(List.of(2), c.leaderChangedInRounds());
        assertEquals(2, c.settledFromRound());

        assertEquals(6, result.graph().arguments().size());
        assertEquals(List.of(new Edge("D1", "P1", Relation.ATTACK)), result.graph().arguments().get(2).edges());
        assertEquals(List.of(), result.graph().pruned());
    }

    @Test
    void capsTheNumberOfArgumentsPerTurnAndValidatesRounds() {
        Result result = simulation().run(CASE, 1, 1, null);

        assertEquals(List.of("P1", "D1"), result.transcript().stream().map(t -> t.argumentId()).toList());
        assertNull(result.comparison().recorded());
        assertThrows(IllegalArgumentException.class, () -> simulation().run(CASE, 0));
        assertThrows(IllegalArgumentException.class, () -> simulation().run(CASE, TrialSimulation.MAX_ROUNDS + 1));
    }

    @Test
    void unknownTargetFallsBackToTheClaim() {
        DefenseAgent defense = new DefenseAgent(m -> new Move(List.of(new Draft("def", "P99"))));
        ProsecutorAgent prosecutor = new ProsecutorAgent(m -> new Move(List.of(new Draft("pros", "C"))));
        JudgeAgent judge = new JudgeAgent(m -> new Assessment(List.of(
                new ArgumentScore("P1", "other", 0.5, "r"), new ArgumentScore("D1", "other", 0.5, "r"))));

        Result result = new TrialSimulation(prosecutor, defense, judge).run(CASE, 1);

        assertEquals(List.of(new Edge("D1", "C", Relation.ATTACK)), result.transcript().get(1).edges());
    }

    @Test
    void aDuplicateThatCannotBeMergedIsWeighedNormally() {
        // השופט טוען ש-D1 חוזר על P1, אבל הם מצדדים שונים: אין מיזוג, והטיעון נשקל
        Result result = simulation(PROSECUTION, DEFENCE, Map.of("D1", "P1")).run(CASE, 1);

        assertEquals(Map.of(), result.pruned());
        assertTrue(result.events().stream().noneMatch(e -> e instanceof Pruned));
        assertEquals(3, result.graph().arguments().size());
        assertNull(result.assessment().get(2).duplicateOf());
    }

    @Test
    void savedSimulationRoundTripsAndIsReusedByTheService(@TempDir Path data) throws Exception {
        Path cases = Files.createDirectories(data.resolve("cases"));
        new ObjectMapper().writeValue(cases.resolve("001-1.json").toFile(), CASE);

        AtomicInteger runs = new AtomicInteger();
        CaseService service = new CaseService(data, data, () -> {
            runs.incrementAndGet();
            return simulation();
        });

        Result first = service.simulate("001-1", 2, false);
        assertTrue(Files.exists(data.resolve("simulations").resolve("001-1.json")));
        assertTrue(Files.exists(data.resolve("simulations").resolve("graphs").resolve("001-1.html")));

        Result again = service.simulate("001-1", 2, false);
        assertEquals(1, runs.get());
        assertEquals(first.predicted(), again.predicted());
        assertEquals(first.claimStrength(), again.claimStrength(), 1e-12);
        assertEquals(first.transcript(), again.transcript());
        assertEquals(first.alternatives(), again.alternatives());

        service.simulate("001-1", 2, true);
        service.simulate("001-1", 1, false);
        assertEquals(3, runs.get());
    }
}
