package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.JudgeAgent.PartyArgument;
import ac.qbaf.legalsim.agents.JudgeAgent.ScoredArgument;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.ArgumentCategory;
import ac.qbaf.legalsim.simulation.RecordedHearing.Presented;
import ac.qbaf.legalsim.simulation.RecordedHearing.Verdict;
import ac.qbaf.legalsim.simulation.RecordedHearing.Weighed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordedHearingTest {
    private static final LegalCase CASE = new LegalCase("001-1", "X v. Y", "facts",
            List.of("public figures must accept criticism", "the fine was excessive"),
            List.of("the words were insulting"), true, LegalCase.VIOLATION);

    private static final List<ScoredArgument> ASSESSMENT = List.of(
            new ScoredArgument(new PartyArgument("A1", "applicant", "a1"), ArgumentCategory.STATUS_OF_PERSON, 0.4, "r"),
            new ScoredArgument(new PartyArgument("A2", "applicant", "a2"), ArgumentCategory.SEVERITY_OF_SANCTION, 0.3, "r"),
            new ScoredArgument(new PartyArgument("G1", "government", "g1"), ArgumentCategory.CONTENT_AND_FORM, 0.6, "r"));

    @Test
    void agentsAlternateAndTheJudgeWeighsAfterEachArgument() {
        List<Object> events = RecordedHearing.replay(CASE, ASSESSMENT);

        assertEquals(7, events.size());
        Presented first = (Presented) events.get(0);
        assertEquals("prosecutor", first.agent());
        assertEquals(LegalCase.VIOLATION, first.decision());
        assertEquals("G1", ((Presented) events.get(2)).argumentId());
        assertEquals("defense", ((Presented) events.get(2)).agent());

        Weighed afterG1 = (Weighed) events.get(3);
        assertEquals("content_and_form", afterG1.category());
        assertEquals(LegalCase.NO_VIOLATION, afterG1.leading());

        Weighed afterA2 = (Weighed) events.get(5);
        assertEquals(2, afterA2.round());
        assertEquals(0.7, afterA2.scores().get(LegalCase.VIOLATION), 1e-9);
        assertEquals(LegalCase.VIOLATION, afterA2.leading());

        Verdict verdict = (Verdict) events.get(6);
        assertEquals(LegalCase.VIOLATION, verdict.predicted());
        assertEquals(LegalCase.VIOLATION, verdict.actual());
    }
}
