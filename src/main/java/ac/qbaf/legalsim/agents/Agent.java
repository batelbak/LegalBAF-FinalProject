package ac.qbaf.legalsim.agents;

import java.util.List;
import java.util.Map;

/** סוכן LLM עם system prompt ייעודי. */
public abstract class Agent {
    public abstract String role();

    public abstract String systemPrompt();

    /** מחזיר מהלך דיון (טיעון / טענת-נגד). */
    public abstract Map<String, Object> respond(Map<String, Object> legalCase, List<Map<String, Object>> history);
}
