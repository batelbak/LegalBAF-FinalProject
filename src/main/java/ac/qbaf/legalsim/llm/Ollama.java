package ac.qbaf.legalsim.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * מודל מקומי דרך Ollama (https://ollama.com), עם structured outputs: התשובה חייבת להתאים ל-JSON Schema
 * שנגזר מהמחלקה, ולכן היא תמיד JSON תקין במבנה הנכון.
 * <p>
 * חלון ההקשר (num_ctx) מחושב לפי אורך ההודעה. ברירת המחדל של Ollama קטנה, והוא חותך הודעות ארוכות בשקט.
 */
public final class Ollama {
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final int MIN_CONTEXT = 8192;
    private static final int MAX_CONTEXT = 131072;
    /** מקום לתשובה עצמה (ולחשיבה, אם מופעלת). */
    private static final int OUTPUT_TOKENS = 4096;
    private static final int ATTEMPTS = 3;

    private final String host;
    private final String model;
    private final boolean think;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Ollama(String host, String model, boolean think) {
        this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        this.model = model;
        this.think = think;
    }

    public String model() {
        return model;
    }

    /** האם שרת Ollama רץ והמודל מותקן. בדיקה מהירה (עד כשתי שניות), לא זורקת. */
    public boolean installed() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return false;
            }
            for (JsonNode m : JSON.readTree(response.body()).path("models")) {
                String name = m.path("name").asText();
                if (name.equals(model) || name.equals(model + ":latest")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public <T> T structured(String systemPrompt, String userMessage, Class<T> type, double temperature) {
        String body = requestBody(systemPrompt, userMessage, type, temperature);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String content = chat(body);
            try {
                return JSON.readValue(content, type);
            } catch (JsonProcessingException e) {
                // נדיר עם structured outputs (למשל תשובה שנחתכה): מנסים שוב
                last = new IllegalStateException("Model " + model + " returned invalid JSON: " + e.getOriginalMessage());
            }
        }
        throw last;
    }

    String requestBody(String systemPrompt, String userMessage, Class<?> type, double temperature) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature);
        options.put("num_ctx", contextFor(systemPrompt.length() + userMessage.length()));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)));
        request.put("format", JsonSchemas.of(type));
        request.put("stream", false);
        request.put("think", think);
        request.put("keep_alive", "30m");
        request.put("options", options);
        try {
            return JSON.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** הערכה זהירה: כ-3 תווים לטוקן, ועוד מקום לתשובה. מעוגל לחזקה של 2. */
    static int contextFor(int promptChars) {
        int needed = promptChars / 3 + OUTPUT_TOKENS;
        int context = MIN_CONTEXT;
        while (context < needed && context < MAX_CONTEXT) {
            context *= 2;
        }
        return context;
    }

    private String chat(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/api/chat"))
                .header("Content-Type", "application/json")
                // מודל שחלקו ב-RAM יכול לחשוב כמה דקות על שקילה של 20 טיעונים
                .timeout(Duration.ofMinutes(30))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException e) {
            throw new IllegalStateException("Ollama is not running at " + host
                    + ". Start it (the Ollama app, or 'ollama serve') and try again.");
        } catch (IOException e) {
            throw new IllegalStateException("Ollama request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Ollama", e);
        }

        JsonNode json;
        try {
            json = JSON.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
        }
        if (response.statusCode() != 200) {
            String error = json.path("error").asText(response.body());
            if (response.statusCode() == 404 && error.contains("not found")) {
                throw new IllegalStateException("Model " + model + " is not installed. Run: ollama pull " + model);
            }
            throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + error);
        }
        String content = json.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("Model " + model + " returned an empty answer (done_reason: "
                    + json.path("done_reason").asText("?") + ")");
        }
        return content;
    }
}
