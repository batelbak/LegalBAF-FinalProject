package ac.qbaf.legalsim.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseLoaderTest {
    private static LegalCase fakeCase(String id, String outcome) {
        return new LegalCase(id, "X v. Y", "facts", List.of("a1"), List.of("g1"), true, outcome);
    }

    @Test
    void fetchesAndExtractsOnceThenReadsFromCache(@TempDir Path dir) {
        AtomicInteger fetches = new AtomicInteger();
        AtomicInteger extractions = new AtomicInteger();
        CaseLoader loader = new CaseLoader(dir,
                id -> {
                    fetches.incrementAndGet();
                    return "judgment text";
                },
                (id, text) -> {
                    extractions.incrementAndGet();
                    assertEquals("judgment text", text);
                    return fakeCase(id, LegalCase.VIOLATION);
                });

        LegalCase first = loader.load("001-1");
        LegalCase second = loader.load("001-1");

        assertEquals(first, second);
        assertEquals(1, fetches.get());
        assertEquals(1, extractions.get());
        assertTrue(Files.exists(dir.resolve("raw/001-1.txt")));
        assertTrue(Files.exists(dir.resolve("cases/001-1.json")));
    }

    @Test
    void validateRejectsUnknownOutcomeAndKeepsOurId() {
        assertThrows(IllegalStateException.class,
                () -> CaseExtractor.validate("001-1", fakeCase("001-1", "partly")));

        LegalCase fixed = CaseExtractor.validate("001-1", fakeCase("model-made-up", LegalCase.NO_VIOLATION));
        assertEquals("001-1", fixed.id());
    }
    @Test
    void htmlToTextKeepsParagraphBreaks() {
        String text = JudgmentSource.htmlToText(
                "<html><body><script>x()</script><h2>THE LAW</h2><p>First  para.</p><p>Second</p></body></html>");

        assertEquals("THE LAW\nFirst para.\nSecond", text);
    }
}
