package ac.qbaf.legalsim.agents;

import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.qbaf.QbafGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * סוכן של צד בדיון (תביעה / הגנה). הוא זה שמכניס את הטיעונים של הצד שלו לגרף,
 * עם קשת תמיכה אל ההחלטה שהצד מבקש. את המשקל של הקשת הוא לא קובע: זה תפקיד השופט.
 */
public abstract class PartyAgent extends Agent {
    /** הצד בפסק הדין: applicant / government. */
    public abstract String side();

    /** קודקוד ההחלטה שהטיעונים של הצד תומכים בו. */
    public abstract String decision();

    /** קידומת למזהי הטיעונים של הצד: A1, A2... / G1, G2... */
    protected abstract String idPrefix();

    /** הטענות האמיתיות של הצד מפסק הדין. */
    protected abstract List<String> submissionsOf(LegalCase legalCase);

    /** מצב א': מכניס לגרף את הטענות האמיתיות של הצד ומחזיר את המזהים שלהן. */
    public List<String> presentSubmissions(QbafGraph graph, LegalCase legalCase) {
        List<String> submissions = submissionsOf(legalCase);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < submissions.size(); i++) {
            String id = argumentId(i);
            present(graph, id, submissions.get(i));
            ids.add(id);
        }
        return ids;
    }

    /** המזהה של הטענה ה-index (מ-0) של הצד: A1, A2... / G1, G2... */
    public String argumentId(int index) {
        return idPrefix() + (index + 1);
    }

    /** הטענות האמיתיות של הצד מפסק הדין. */
    public List<String> submissions(LegalCase legalCase) {
        return submissionsOf(legalCase);
    }

    /** קודקוד טיעון חדש וקשת תמיכה בלי משקל אל ההחלטה של הצד. */
    public void present(QbafGraph graph, String argumentId, String text) {
        graph.addArgument(argumentId, text);
        graph.addSupport(argumentId, decision());
    }
}
