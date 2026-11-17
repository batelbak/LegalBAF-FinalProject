package ac.qbaf.legalsim.llm;

import ac.qbaf.legalsim.agents.JudgeAgent.Assessment;
import ac.qbaf.legalsim.data.LegalCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonSchemasTest {
    @Test
    @SuppressWarnings("unchecked")
    void nestedRecordsListsAndDescriptions() {
        Map<String, Object> schema = JsonSchemas.of(Assessment.class);

        assertEquals(List.of("scores"), schema.get("required"));
        Map<String, Object> scores = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("scores");
        Map<String, Object> item = (Map<String, Object>) scores.get("items");
        Map<String, Object> props = (Map<String, Object>) item.get("properties");
        assertEquals(List.of("argumentId", "category", "strength", "duplicateOf", "rationale"), item.get("required"));
        assertEquals("number", ((Map<String, Object>) props.get("strength")).get("type"));
        assertEquals("The argument id exactly as given, e.g. A1 or G3",
                ((Map<String, Object>) props.get("argumentId")).get("description"));
        assertEquals(false, item.get("additionalProperties"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void caseExtractionSchemaHasBooleansAndStringLists() {
        Map<String, Object> props = (Map<String, Object>) JsonSchemas.of(LegalCase.class).get("properties");

        assertEquals("boolean", ((Map<String, Object>) props.get("submissionsSectionFound")).get("type"));
        assertEquals("array", ((Map<String, Object>) props.get("applicantSubmissions")).get("type"));
    }

    record Unsupported(Object anything) {
    }

    @Test
    void rejectsTypesItCannotDescribe() {
        assertThrows(IllegalArgumentException.class, () -> JsonSchemas.of(Unsupported.class));
    }
}
