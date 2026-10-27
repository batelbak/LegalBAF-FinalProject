package ac.qbaf.legalsim.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** מגיש את קבצי הפרונטאנד מתיקיית frontend. */
public class StaticFileHandler implements HttpHandler {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/")) {
                path += "index.html";
            }
            // לא מגישים שום דבר מחוץ לתיקיית frontend
            Path file = root.resolve(path.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                byte[] body = "Not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
                return;
            }
            String name = file.getFileName().toString();
            String extension = name.substring(name.lastIndexOf('.') + 1);
            exchange.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(extension, "application/octet-stream"));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            byte[] body = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
