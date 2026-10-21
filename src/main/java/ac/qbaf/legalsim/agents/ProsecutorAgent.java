package ac.qbaf.legalsim.agents;

import java.util.List;
import java.util.Map;

/** סוכן התביעה: טיעונים וטענות-נגד לטובת הרשעה / קבלת התביעה. */
public class ProsecutorAgent extends Agent {
    @Override
    public String role() {
        return "prosecutor";
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
