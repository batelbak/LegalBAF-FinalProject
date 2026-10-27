package ac.qbaf.legalsim.qbaf;

/**
 * קטגוריות הטיעון בתיקי סעיף 10 (עיתונות ולשון הרע).
 * 1–6 הם שיקולי Axel Springer; לכל קטגוריה ה-LP ילמד משקל θ משלה.
 */
public enum ArgumentCategory {
    PUBLIC_INTEREST("public_interest",
            "Whether the publication contributed to a debate of public interest"),
    STATUS_OF_PERSON("status_of_person",
            "How well known the person concerned is: politician, official, public figure or private individual"),
    PRIOR_CONDUCT("prior_conduct",
            "The prior conduct of the person concerned, e.g. having sought publicity or raised the issue themselves"),
    TRUTH_AND_VERIFICATION("truth_and_verification",
            "Factual basis, verification of facts, good faith, value judgments versus statements of fact"),
    CONTENT_AND_FORM("content_and_form",
            "Content, form and tone of the publication, e.g. exaggeration, provocation, insulting words, satire"),
    SEVERITY_OF_SANCTION("severity_of_sanction",
            "Nature and severity of the sanction, criminal versus civil proceedings, chilling effect"),
    LAWFULNESS_AND_AIM("lawfulness_and_aim",
            "Whether the interference was prescribed by law and pursued a legitimate aim"),
    OTHER("other",
            "Anything that fits none of the categories above");

    private final String id;
    private final String description;

    ArgumentCategory(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public static ArgumentCategory fromId(String id) {
        for (ArgumentCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown argument category: " + id);
    }
}
