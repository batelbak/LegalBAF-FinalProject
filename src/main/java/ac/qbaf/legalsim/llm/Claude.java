package ac.qbaf.legalsim.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;

/** קריאה ל-Claude שמחזירה JSON מובנה לפי מחלקת Java. */
public final class Claude {
    public static final String API_KEY_ENV = "LEGALSIM_API_KEY";
    public static final String MODEL = "claude-opus-5-5";

    private static AnthropicClient client;

    public static <T> T structured(String systemPrompt, String userMessage, Class<T> type) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(systemPrompt)
                .outputConfig(type)
                .addUserMessage(userMessage)
                .build();

        StructuredMessage<T> response = client().messages().create(params);
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No structured output, stop reason: " + response.stopReason()));
    }

    private static synchronized AnthropicClient client() {
        if (client == null) {
            String apiKey = System.getenv(API_KEY_ENV);
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Environment variable " + API_KEY_ENV + " is not set");
            }
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
        return client;
    }

    private Claude() {
    }
}
