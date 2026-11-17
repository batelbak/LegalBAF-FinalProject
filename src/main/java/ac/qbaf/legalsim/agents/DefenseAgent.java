package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph.Relation;

import java.util.List;
import java.util.function.Function;

/**
 * סוכן ההגנה: חורים בראיות וטיעוני נגד.
 * בתיקי סעיף 10 הנאשם הוא המדינה, ולכן ההגנה היא צד הממשלה: היא תוקפת את הטענה המרכזית C
 * ואת טיעוני המבקש.
 */
public class DefenseAgent extends PartyAgent {
    public static final String SIDE = "government";

    public DefenseAgent() {
    }

    /** llm מזויף לטסטים. */
    public DefenseAgent(Function<String, Move> llm) {
        super(llm);
    }

    /** llm ו-linker מזויפים לטסטים. */
    public DefenseAgent(Function<String, Move> llm, Function<String, Links> linker) {
        super(llm, linker);
    }

    @Override
    protected Relation towardsClaim() {
        return Relation.ATTACK;
    }

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
    protected String simulatedIdPrefix() {
        return "D";
    }

    @Override
    protected List<String> submissionsOf(LegalCase legalCase) {
        return legalCase.governmentSubmissions();
    }

    @Override
    protected String brief() {
        return "You are the Agent of the respondent Government before the European Court of Human Rights, in an "
                + "Article 10 (freedom of expression) complaint concerning the press and defamation. You argue "
                + "that the domestic authorities' interference was prescribed by law, pursued a legitimate aim, "
                + "was necessary in a democratic society, and therefore did not violate Article 10.";
    }
}
