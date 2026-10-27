package ac.qbaf.legalsim.data;

import ac.qbaf.legalsim.llm.Claude;

/** מחלץ מטקסט פסק הדין את העובדות, טענות הצדדים וההכרעה, בעזרת Claude. */
public class CaseExtractor {
    private static final String SYSTEM_PROMPT = """
            You extract structured data from judgments of the European Court of Human Rights \
            on Article 10 (freedom of expression) for a research simulation.

            - facts: a detailed, faithful and neutral account of the background and the domestic \
            proceedings, including what the domestic courts decided and why. Quote the impugned \
            statements or publication verbatim. Exclude everything from the Strasbourg Court's own \
            assessment, and anything that reveals how the Court ruled.
            - applicantSubmissions / governmentSubmissions: each party's arguments on the merits of \
            the Article 10 complaint before the Court, one self-contained argument per item, written \
            as that party's position. Use the parties' submissions section. If the judgment has no \
            such section, collect the arguments the text attributes to each party, set \
            submissionsSectionFound to false, and never include the Court's own reasoning.
            - outcome: the Court's conclusion on Article 10 from the operative part, exactly \
            "violation" or "no_violation".""";

    public LegalCase extract(String caseId, String judgmentText) {
        LegalCase extracted = Claude.structured(SYSTEM_PROMPT,
                "Case id: " + caseId + "\n\n<judgment>\n" + judgmentText + "\n</judgment>",
                LegalCase.class);
        return validate(caseId, extracted);
    }

    static LegalCase validate(String caseId, LegalCase extracted) {
        if (!LegalCase.VIOLATION.equals(extracted.outcome()) && !LegalCase.NO_VIOLATION.equals(extracted.outcome())) {
            throw new IllegalStateException("Unexpected outcome for " + caseId + ": " + extracted.outcome());
        }
        // המזהה נקבע על ידינו, לא על ידי המודל
        return new LegalCase(caseId, extracted.title(), extracted.facts(), extracted.applicantSubmissions(),
                extracted.governmentSubmissions(), extracted.submissionsSectionFound(), extracted.outcome());
    }
}
