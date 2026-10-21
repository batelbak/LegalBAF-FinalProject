package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.qbaf.QbafGraph;

import java.util.List;
import java.util.Map;

/** סוכן השופט: ממפה טיעונים ל-QBAF ומכריע מי מוביל בגרף. */
public class JudgeAgent extends Agent {
    @Override
    public String role() {
        return "judge";
    }

    @Override
    public String systemPrompt() {
        return "";
    }

    @Override
    public Map<String, Object> respond(Map<String, Object> legalCase, List<Map<String, Object>> history) {
        throw new UnsupportedOperationException("skeleton");
    }

    public QbafGraph mapToGraph(List<Map<String, Object>> history) {
        throw new UnsupportedOperationException("skeleton");
    }

    public Map<String, Object> decide(QbafGraph graph) {
        throw new UnsupportedOperationException("skeleton");
    }
}
