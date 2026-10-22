package ac.qbaf.legalsim.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * טעינת תיקים מבית הדין האירופי (סעיף 10, עיתונות ולשון הרע).
 * <p>
 * לכל תיק: הטקסט נשמר ב-data/raw/&lt;id&gt;.txt והחילוץ ב-data/cases/&lt;id&gt;.json.
 * ריצה חוזרת קוראת מהקבצים ולא מורידה או מחלצת שוב.
 */
public class CaseLoader {
    /** 5 התיקים של שלב א'. */
    public static final List<String> SAMPLE_IDS = List.of(
            "001-164453", // Ziembiński v. Poland (No. 2)
            "001-127395", // Ristamäki and Korvola v. Finland
            "001-102358", // Novaya Gazeta v Voronezhe v. Russia
            "001-59207",  // Tammer v. Estonia
            "001-85085"); // Rumyana Ivanova v. Bulgaria

    @FunctionalInterface
    public interface TextFetcher {
        String fetchText(String caseId) throws IOException, InterruptedException;
    }

    private final Path dataDir;
    private final TextFetcher fetcher;
    private BiFunction<String, String, LegalCase> extractor;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public CaseLoader() {
        this(Path.of("data"), new JudgmentSource()::fetchText, null);
    }

    /** extractor == null: CaseExtractor אמיתי, שנוצר רק כשצריך לחלץ תיק חדש. */
    public CaseLoader(Path dataDir, TextFetcher fetcher, BiFunction<String, String, LegalCase> extractor) {
        this.dataDir = dataDir;
        this.fetcher = fetcher;
        this.extractor = extractor;
    }

    public List<LegalCase> loadSample() {
        List<LegalCase> cases = new ArrayList<>();
        for (String id : SAMPLE_IDS) {
            cases.add(load(id));
        }
        return cases;
    }

    public LegalCase load(String caseId) {
        try {
            Path casePath = dataDir.resolve("cases").resolve(caseId + ".json");
            if (Files.exists(casePath)) {
                return json.readValue(casePath.toFile(), LegalCase.class);
            }
            LegalCase extracted = extractor().apply(caseId, rawText(caseId));
            Files.createDirectories(casePath.getParent());
            json.writeValue(casePath.toFile(), extracted);
            return extracted;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load case " + caseId, e);
        }
    }

    private String rawText(String caseId) throws IOException {
        Path rawPath = dataDir.resolve("raw").resolve(caseId + ".txt");
        if (Files.exists(rawPath)) {
            return Files.readString(rawPath, StandardCharsets.UTF_8);
        }
        try {
            String text = fetcher.fetchText(caseId);
            Files.createDirectories(rawPath.getParent());
            Files.writeString(rawPath, text, StandardCharsets.UTF_8);
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + caseId, e);
        }
    }

    private BiFunction<String, String, LegalCase> extractor() {
        if (extractor == null) {
            extractor = new CaseExtractor()::extract;
        }
        return extractor;
    }
}
