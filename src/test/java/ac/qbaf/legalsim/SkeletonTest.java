package ac.qbaf.legalsim;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.simulation.TrialSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkeletonTest {
    @Test
    void agentsExist() {
        assertEquals("prosecutor", new ProsecutorAgent().role());
        assertEquals("defense", new DefenseAgent().role());
        assertEquals("judge", new JudgeAgent().role());
    }

    @Test
    void trialIsStub() {
        assertThrows(UnsupportedOperationException.class, () -> new TrialSimulation().run());
    }
}
