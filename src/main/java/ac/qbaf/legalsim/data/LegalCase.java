package ac.qbaf.legalsim.data;

import ac.qbaf.legalsim.qbaf.QbafGraph;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * תיק מבית הדין האירופי (סעיף 10), כפי שחולץ מפסק הדין.
 * הסוכנים מקבלים רק את facts. outcome משמש לכיול ולהשוואה בלבד.
 */
public record LegalCase(
        @JsonPropertyDescription("HUDOC item id, e.g. 001-164453")
        String id,
        @JsonPropertyDescription("Case title, e.g. 'Ziembiński v. Poland (No. 2)'")
        String title,
        @JsonPropertyDescription("Detailed, neutral account of the facts and the domestic proceedings. "
                + "Quote the impugned publication verbatim. Must not reveal the Strasbourg Court's reasoning or outcome.")
        String facts,
        @JsonPropertyDescription("The applicant's arguments on the merits of Article 10, one argument per item")
        List<String> applicantSubmissions,
        @JsonPropertyDescription("The Government's arguments on the merits of Article 10, one argument per item")
        List<String> governmentSubmissions,
        @JsonPropertyDescription("True if the judgment has a dedicated section for the parties' submissions; "
                + "false if the arguments were collected from elsewhere in the text")
        boolean submissionsSectionFound,
        @JsonPropertyDescription("The Court's conclusion on Article 10: exactly 'violation' or 'no_violation'")
        String outcome) {

    public static final String VIOLATION = "violation";
    public static final String NO_VIOLATION = "no_violation";

    public static final String CLAIM_TEXT =
            "The interference with the applicant's freedom of expression violated Article 10 of the Convention.";

    /**
     * גרף שיש בו רק את הטענה המרכזית C, עם ציון בסיס ניטרלי. את הטיעונים מכניסים הסוכנים:
     * כל טיעון תוקף או תומך ב-C או בטיעון קודם.
     */
    public static QbafGraph emptyGraph() {
        return new QbafGraph(CLAIM_TEXT);
    }
}
