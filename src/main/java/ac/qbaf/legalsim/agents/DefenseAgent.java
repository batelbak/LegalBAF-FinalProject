package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.data.LegalCase;

import java.util.List;
import java.util.Map;

/**
 * סוכן ההגנה: חורים בראיות וטיעוני נגד.
 * בתיקי סעיף 10 הנאשם הוא המדינה, ולכן ההגנה היא צד הממשלה והטיעונים שלה תומכים ב-no_violation.
 */
public class DefenseAgent extends PartyAgent {
    public static final String SIDE = "government";

    @Override
    public String role() {
        return "defense";
    }

    @Override
    public String side() {
        return SIDE;
    }

    @Override
    public String decision() {
        return LegalCase.NO_VIOLATION;
    }

    @Override
    protected String idPrefix() {
        return "G";
    }

    @Override
    protected List<String> submissionsOf(LegalCase legalCase) {
        return legalCase.governmentSubmissions();
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
