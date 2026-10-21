package ac.qbaf.legalsim.simulation;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.ProsecutorAgent;

import java.util.Map;

/** סימולציית משפט אדברסרית — סבבי טיעונים בין הסוכנים. */
public class TrialSimulation {
    private final ProsecutorAgent prosecutor = new ProsecutorAgent();
    private final DefenseAgent defense = new DefenseAgent();
    private final JudgeAgent judge = new JudgeAgent();

    public Map<String, Object> run() {
        return run(null, 3);
    }

    public Map<String, Object> run(Map<String, Object> legalCase, int rounds) {
        throw new UnsupportedOperationException("skeleton");
    }
}
