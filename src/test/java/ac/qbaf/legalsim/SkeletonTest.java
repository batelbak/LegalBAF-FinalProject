package ac.qbaf.legalsim;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkeletonTest {
    @Test
    void agentsExist() {
        assertEquals("prosecutor", new ProsecutorAgent().role());
        assertEquals("defense", new DefenseAgent().role());
        assertEquals("judge", new JudgeAgent().role());
    }
}
