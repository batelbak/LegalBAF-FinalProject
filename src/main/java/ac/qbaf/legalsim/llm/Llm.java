package ac.qbaf.legalsim.llm;

import java.nio.file.Path;

/**
 * הנקודה היחידה שבה הקוד פונה למודל שפה. ברירת המחדל: מודל מקומי דרך Ollama, בלי מפתח API ובלי עלות.
 * <p>
 * משתני סביבה (כולם אופציונליים):
 * <ul>
 *   <li>LEGALSIM_LLM — ollama (ברירת מחדל) או claude</li>
 *   <li>LEGALSIM_MODEL — המודל המקומי, ברירת מחדל gemma4:12b</li>
 *   <li>OLLAMA_HOST — כתובת שרת Ollama, ברירת מחדל http://localhost:11434</li>
 *   <li>LEGALSIM_THINK — true כדי להפעיל מצב חשיבה במודלים שתומכים בו (איטי יותר)</li>
 *   <li>LEGALSIM_API_KEY — רק עם LEGALSIM_LLM=claude</li>
 * </ul>
 * <p>
 * כל מודל מקבל תיקיית תוצאות משלו (profile), כך שהערכות של שופט אחד לא מתערבבות עם של שופט אחר.
 * ההשוואה בין הסימולציה לטענות האמיתיות הוגנת רק כששני הגרפים נשקלו על ידי אותו שופט.
 */
public final class Llm {
    public static final String PROVIDER_ENV = "LEGALSIM_LLM";
    public static final String MODEL_ENV = "LEGALSIM_MODEL";
    public static final String THINK_ENV = "LEGALSIM_THINK";
    public static final String DEFAULT_MODEL = "gemma4:12b";
    public static final String DEFAULT_HOST = "http://localhost:11434";

    /** טמפרטורה לשופט ולחילוץ: עקביות. */
    public static final double PRECISE = 0.0;
    /** טמפרטורה לעורכי הדין: מגוון טיעונים. */
    public static final double CREATIVE = 0.7;

    private static Ollama ollama;

    public static <T> T structured(String systemPrompt, String userMessage, Class<T> type, double temperature) {
        if (usesClaude()) {
            return Claude.structured(systemPrompt, userMessage, type);
        }
        return ollama().structured(systemPrompt, userMessage, type, temperature);
    }

    /** האם אפשר להריץ את המודל עכשיו: ל-Claude — יש מפתח; למודל מקומי — Ollama רץ והמודל מותקן. */
    public static boolean available() {
        if (usesClaude()) {
            String key = System.getenv(Claude.API_KEY_ENV);
            return key != null && !key.isBlank();
        }
        return ollama().installed();
    }

    public static boolean usesClaude() {
        return "claude".equalsIgnoreCase(env(PROVIDER_ENV, "ollama"));
    }

    /** המודל שבשימוש, לתצוגה ולשמות תיקיות. */
    public static String model() {
        return usesClaude() ? Claude.MODEL : env(MODEL_ENV, DEFAULT_MODEL);
    }

    /** שם קצר לתיקייה: ollama-gemma4-12b. */
    public static String profile() {
        return usesClaude() ? "claude" : "ollama-" + model().replaceAll("[^A-Za-z0-9.]+", "-").toLowerCase();
    }

    /**
     * איפה נשמרים ההערכות, הגרפים והסימולציות של המודל הנוכחי.
     * Claude: ישירות ב-data (המבנה המקורי של שלב א'). מודל מקומי: data/local/&lt;profile&gt;.
     * התיקים עצמם (data/cases, data/raw) משותפים לכל המודלים.
     */
    public static Path resultsDir(Path dataDir) {
        return usesClaude() ? dataDir : dataDir.resolve("local").resolve(profile());
    }

    private static synchronized Ollama ollama() {
        if (ollama == null) {
            ollama = new Ollama(env("OLLAMA_HOST", DEFAULT_HOST), model(),
                    Boolean.parseBoolean(env(THINK_ENV, "false")));
        }
        return ollama;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        // OLLAMA_HOST לפעמים מוגדר בלי http:// (למשל 0.0.0.0:11434 כשמשתפים ברשת)
        if (name.equals("OLLAMA_HOST") && !value.startsWith("http")) {
            value = "http://" + value.replace("0.0.0.0", "localhost");
        }
        return value;
    }

    private Llm() {
    }
}
