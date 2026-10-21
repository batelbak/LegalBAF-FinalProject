package ac.qbaf.legalsim.agents;

import java.util.List;
import java.util.Map;

/** סוכן ההגנה: חורים בראיות וטיעוני נגד. */
public class DefenseAgent extends Agent {
    @Override
    public String role() {
        return "defense";
    }

    @Override
    public String systemPrompt() {
        return "";
    }

    @Override
    public Map<String, Object> respond(Map<String, Object> legalCase, List<Map<String, Object>> history) {
        throw new UnsupportedOperationException("skeleton");
    }
}
