package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;

import java.util.List;
import java.util.function.Function;

/**
 * סוכן התביעה: טיעונים וטענות-נגד לטובת הרשעה / קבלת התביעה.
 * בתיקי סעיף 10 הנאשם הוא המדינה, ולכן התביעה היא צד המבקש: היא תומכת בטענה המרכזית C (הפרה)
 * ותוקפת את טיעוני הממשלה.
 */
public class ProsecutorAgent extends PartyAgent {
    public static final String SIDE = "applicant";

    public ProsecutorAgent() {
    }

    /** llm מזויף לטסטים. */
    public ProsecutorAgent(Function<String, Move> llm) {
        super(llm);
    }

    /** llm ו-linker מזויפים לטסטים. */
    public ProsecutorAgent(Function<String, Move> llm, Function<String, Links> linker) {
        super(llm, linker);
    }

    @Override
    protected Relation towardsClaim() {
        return Relation.SUPPORT;
    }

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
    protected String simulatedIdPrefix() {
        return "P";
    }

    @Override
    protected List<String> submissionsOf(LegalCase legalCase) {
        return legalCase.applicantSubmissions();
    }

    @Override
    protected String brief() {
        return "You are counsel for the applicant before the European Court of Human Rights, in an Article 10 "
                + "(freedom of expression) complaint concerning the press and defamation. You argue that the "
                + "domestic authorities' interference with the applicant's freedom of expression violated "
                + "Article 10 of the Convention.";
    }
}
