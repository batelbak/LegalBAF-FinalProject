package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.JudgeAgent.PartyArgument;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.qbaf.QbafGraph.Edge;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;
import ac.qbaf.legalsim.simulation.RecordedHearing.Built;
import ac.qbaf.legalsim.simulation.RecordedHearing.Presented;
import ac.qbaf.legalsim.simulation.RecordedHearing.Pruned;
import ac.qbaf.legalsim.simulation.RecordedHearing.Verdict;
import ac.qbaf.legalsim.simulation.RecordedHearing.Weighed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordedHearingTest {
    private static final LegalCase CASE = new LegalCase("001-1", "X v. Y", "facts",
            List.of("public figures must accept criticism", "the fine was excessive"),
            List.of("the words were insulting"), true, LegalCase.VIOLATION);

    private static ScoredArgument scored(String id, String side, ArgumentCategory category, double strength,
                                         String duplicateOf) {
        return new ScoredArgument(new PartyArgument(id, side, id), category, strength, "r", duplicateOf);
    }

    private static final List<ScoredArgument> ASSESSMENT = List.of(
            scored("A1", "applicant", ArgumentCategory.STATUS_OF_PERSON, 0.4, null),
            scored("A2", "applicant", ArgumentCategory.SEVERITY_OF_SANCTION, 0.3, null),
            scored("G1", "government", ArgumentCategory.CONTENT_AND_FORM, 0.6, null));

    @Test
    void submissionsAlternateAndFollowTheStructure() {
        // G1 עונה ל-A1; A2 תומך ב-C
        List<Object> events = RecordedHearing.replay(CASE, ASSESSMENT,
                Map.of("G1", List.of("A1"), "A2", List.of("C")));

        assertEquals(7, events.size());
        Presented first = (Presented) events.get(0);
        assertEquals("prosecutor", first.agent());
        assertEquals(List.of(new Edge("A1", "C", Relation.SUPPORT)), first.edges());
        Presented g1 = (Presented) events.get(2);
        assertEquals("G1", g1.argumentId());
        assertEquals(List.of(new Edge("G1", "A1", Relation.ATTACK)), g1.edges());

        // אחרי A1: σ(C) = 0.5 + 0.5·0.4 = 0.7
        assertEquals(0.7, ((Weighed) events.get(1)).claimStrength(), 1e-9);
        // אחרי G1: A1 = 0.4 − 0.4·0.6 = 0.16 → σ(C) = 0.58
        Weighed afterG1 = (Weighed) events.get(3);
        assertEquals(0.16, afterG1.strengths().get("A1"), 1e-9);
        assertEquals(0.58, afterG1.claimStrength(), 1e-9);
        assertEquals(LegalCase.VIOLATION, afterG1.leading());

        Weighed afterA2 = (Weighed) events.get(5);
        assertEquals(2, afterA2.round());
        assertEquals(0.3, afterA2.baseScore(), 1e-9);

        Verdict verdict = (Verdict) events.get(6);
        assertEquals(LegalCase.VIOLATION, verdict.predicted());
        assertEquals(LegalCase.VIOLATION, verdict.actual());
        assertEquals("DF-QuAD", verdict.semantics());
        assertEquals(verdict.claimStrength(), verdict.alternatives().get("df-quad").claimStrength(), 1e-12);
        assertEquals(3, verdict.alternatives().size());
    }

    @Test
    void aSubmissionAnsweringALaterOneEntersTheGraphAfterIt() {
        // G1 ("insulting") עונה ל-A2, שמופיע אחריו בסדר הבסיסי A1, G1, A2: הסדר נעשה A1, A2, G1
        Map<String, List<String>> structure = Map.of("A1", List.of("C"), "A2", List.of("C"), "G1", List.of("A2"));

        assertEquals(List.of("A1", "A2", "G1"),
                RecordedHearing.order(CASE, structure).stream().map(s -> s.id()).toList());
        Built built = RecordedHearing.build(CASE, ASSESSMENT, structure);
        assertEquals(List.of(new Edge("G1", "A2", Relation.ATTACK)), built.graph().edgesFrom("G1"));
        assertEquals(2, built.graph().depth("G1"));
        // בלי קישורים קדימה הסדר נשאר לסירוגין, כמו קודם
        assertEquals(List.of("A1", "G1", "A2"), RecordedHearing.order(CASE,
                Map.of("A1", List.of("C"), "G1", List.of("A1"), "A2", List.of("C"))).stream().map(s -> s.id()).toList());
    }

    @Test
    void aSubmissionCanAnswerSeveralNodes() {
        // G1 תוקף גם את A1 וגם את C; A2 עונה ל-G1 ומחזק את A1
        Map<String, List<String>> structure = Map.of("G1", List.of("A1", "C"), "A2", List.of("G1", "A1"));

        Built built = RecordedHearing.build(CASE, ASSESSMENT, structure);

        assertEquals(List.of(new Edge("G1", "A1", Relation.ATTACK), new Edge("G1", "C", Relation.ATTACK)),
                built.graph().edgesFrom("G1"));
        assertEquals(List.of(new Edge("A2", "G1", Relation.ATTACK), new Edge("A2", "A1", Relation.SUPPORT)),
                built.graph().edgesFrom("A2"));
        // G1 = 0.6·(1 − 0.3) = 0.42. A1: תומך 0.3, תוקף 0.42 → 0.4·(1 − 0.12) = 0.352.
        // C: תומך 0.352, תוקף 0.42 → 0.5·(1 − 0.068) = 0.466
        Verdict verdict = (Verdict) built.events().get(built.events().size() - 1);
        assertEquals(0.42, verdict.strengths().get("G1"), 1e-9);
        assertEquals(0.352, verdict.strengths().get("A1"), 1e-9);
        assertEquals(0.466, verdict.claimStrength(), 1e-9);
        assertEquals(LegalCase.NO_VIOLATION, verdict.predicted());
    }

    @Test
    void aDuplicateFlaggedByTheJudgeIsMergedAndNotCountedTwice() {
        // השופט קבע ש-A2 חוזר על A1. A2 כוון נגד G1, אבל A1 לא יכול לתקוף את מי שתוקף אותו: הקשת מושמטת
        List<ScoredArgument> assessment = List.of(ASSESSMENT.get(0),
                scored("A2", "applicant", ArgumentCategory.OTHER, 0.9, "A1"), ASSESSMENT.get(2));

        Built built = RecordedHearing.build(CASE, assessment, Map.of("G1", List.of("A1"), "A2", List.of("C", "G1")));

        assertEquals(Map.of("A2", "A1"), built.pruned());
        assertFalse(built.graph().contains("A2"));
        Pruned pruned = (Pruned) built.events().get(5);
        assertEquals("A2", pruned.argumentId());
        assertEquals("A1", pruned.duplicateOf());
        assertEquals(List.of(), pruned.edges());
        // ההכרעה כאילו A2 לא נטען: A1 = 0.16 → σ(C) = 0.58
        assertEquals(0.58, pruned.claimStrength(), 1e-9);
        assertEquals(0.58, ((Verdict) built.events().get(6)).claimStrength(), 1e-9);
    }

    @Test
    void aLaterSubmissionAimedAtAMergedOneIsRedirectedToTheOriginal() {
        LegalCase longer = new LegalCase("001-2", "X v. Y", "facts", List.of("a1", "a2"), List.of("g1", "g2"), true,
                LegalCase.VIOLATION);
        List<ScoredArgument> assessment = List.of(
                scored("A1", "applicant", ArgumentCategory.OTHER, 0.5, null),
                scored("G1", "government", ArgumentCategory.OTHER, 0.5, null),
                scored("A2", "applicant", ArgumentCategory.OTHER, 0.5, "A1"),
                scored("G2", "government", ArgumentCategory.OTHER, 0.5, null));

        // G2 עונה ל-A2, שמוזג אל A1
        Built built = RecordedHearing.build(longer, assessment, Map.of("G2", List.of("A2")));

        assertEquals(List.of(new Edge("G2", "A1", Relation.ATTACK)), built.graph().edgesFrom("G2"));
        assertTrue(built.events().stream().anyMatch(e -> e instanceof Pruned));
    }

    @Test
    void withoutStructureEverySubmissionAnswersTheClaim() {
        List<Object> events = RecordedHearing.replay(CASE, ASSESSMENT);

        Presented g1 = (Presented) events.get(2);
        assertEquals(List.of(new Edge("G1", "C", Relation.ATTACK)), g1.edges());
        // תומכים 0.4, 0.3 → 0.58; תוקף 0.6 → σ(C) = 0.5 − 0.5·0.02 = 0.49
        assertEquals(0.49, ((Verdict) events.get(6)).claimStrength(), 1e-9);
        assertEquals(LegalCase.NO_VIOLATION, ((Verdict) events.get(6)).predicted());
    }
}
