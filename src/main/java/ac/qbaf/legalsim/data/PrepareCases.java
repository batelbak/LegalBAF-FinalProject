package ac.qbaf.legalsim.data;

/**
 * מוריד ומחלץ את תיקי המדגם. ארגומנט אופציונלי: כמה תיקים לעבד (ברירת מחדל: כולם).
 * mvn compile exec:java -Dexec.mainClass=ac.qbaf.legalsim.data.PrepareCases -Dexec.args=1
 */
public final class PrepareCases {
    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : CaseLoader.SAMPLE_IDS.size();
        CaseLoader loader = new CaseLoader();
        for (String id : CaseLoader.SAMPLE_IDS.subList(0, count)) {
            LegalCase c = loader.load(id);
            System.out.printf("%s  %s  outcome=%s  applicant=%d  government=%d  section=%s%n",
                    c.id(), c.title(), c.outcome(), c.applicantSubmissions().size(),
                    c.governmentSubmissions().size(), c.submissionsSectionFound());
        }
    }

    private PrepareCases() {
    }
}
