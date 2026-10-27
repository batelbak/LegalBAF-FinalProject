package ac.qbaf.legalsim.data;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * מוריד את הטקסט המלא של פסק דין מאתר המראה ferenda.lagen.nu.
 * HUDOC עצמו מחזיר דף JavaScript ריק, ולכן לא משתמשים בו.
 */
public class JudgmentSource {
    private static final String MIRROR_URL = "https://ferenda.lagen.nu/dom/echr/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String fetchText(String caseId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MIRROR_URL + caseId))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Fetching " + caseId + " failed: HTTP " + response.statusCode());
        }
        return htmlToText(response.body());
    }

    /** ממיר HTML לטקסט, שורה לכל פסקה או כותרת. */
    static String htmlToText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, nav, header, footer").remove();
        doc.select("br").append("\\n");
        doc.select("p, h1, h2, h3, h4, h5, h6, li, div").prepend("\\n");
        return doc.body().text()
                .replace("\\n", "\n")
                .replaceAll("[ \\t]*\n[ \\t]*", "\n")
                .replaceAll("\n{2,}", "\n")
                .strip();
    }
}
