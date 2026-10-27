package ac.qbaf.legalsim.web;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * השרת של האתר: מגיש את הפרונטאנד (תיקיית frontend) ואת ה-API (/api).
 * מריצים מתיקיית הפרויקט:
 * mvn compile exec:java -Dexec.mainClass=ac.qbaf.legalsim.web.WebServer
 * ואז פותחים http://localhost:8080
 */
public final class WebServer {
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path frontend = Path.of("frontend");
        if (!Files.isDirectory(frontend)) {
            throw new IllegalStateException("Run the server from the project folder: no frontend folder in "
                    + Path.of("").toAbsolutePath());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/api/", new ApiHandler(new CaseService(Path.of("data"))));
        server.createContext("/", new StaticFileHandler(frontend));
        server.start();
        System.out.println("LegalBAF is running at http://localhost:" + port);
    }

    private WebServer() {
    }
}
