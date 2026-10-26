package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.data.LegalCase;

import java.util.List;
import java.util.Map;

/**
 * סוכן התביעה: טיעונים וטענות-נגד לטובת הרשעה / קבלת התביעה.
 * בתיקי סעיף 10 הנאשם הוא המדינה, ולכן התביעה היא צד המבקש והטיעונים שלה תומכים ב-violation.
 */
public class ProsecutorAgent extends PartyAgent {
    public static final String SIDE = "applicant";

    @Override
    public String role() {
        return "prosecutor";
    }

    @Override
    public String side() {
        return SIDE;
    }

    @Override
    public String decision() {
        return LegalCase.VIOLATION;
    }

    @Override
    protected String idPrefix() {
        return "A";
    }

    @Override
    protected List<String> submissionsOf(LegalCase legalCase) {
        return legalCase.applicantSubmissions();
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
