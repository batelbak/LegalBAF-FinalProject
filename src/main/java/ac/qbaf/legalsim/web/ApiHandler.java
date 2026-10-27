package ac.qbaf.legalsim.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ה-REST API שהפרונטאנד קורא לו. כל התשובות ב-JSON.
 * <pre>
 * GET  /api/health
 * GET  /api/cases
 * GET  /api/cases/{id}
 * GET  /api/cases/{id}/hearing          מצב א': הטענות האמיתיות והשקילה של השופט
 * GET  /api/cases/{id}/tipping-points   501 עד שיש TippingPointAnalyzer
 * POST /api/simulations                 {caseId, rounds} — 501 עד שיש TrialSimulation
 * POST /api/strategy                    {caseId, side, text} — 501 עד שיש LitigationStrategy
 * </pre>
 */
public class ApiHandler implements HttpHandler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CASE_PATH = Pattern.compile("/api/cases/([^/]+)(/[a-z-]+)?/?");

    private final CaseService cases;

    public ApiHandler(CaseService cases) {
        this.cases = cases;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Object body = route(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange);
            send(exchange, 200, body);
        } catch (CaseService.NotFoundException e) {
            send(exchange, 404, error("not_found", e.getMessage()));
        } catch (UnsupportedOperationException e) {
            send(exchange, 501, error("not_implemented", "This part of the system is not implemented yet."));
        } catch (BadRequestException e) {
            send(exchange, 400, error("bad_request", e.getMessage()));
        } catch (Exception e) {
            send(exchange, 500, error("server_error", String.valueOf(e.getMessage())));
        } finally {
            exchange.close();
        }
    }

    private Object route(String method, String path, HttpExchange exchange) throws IOException {
        if (path.equals("/api/health")) {
            return Map.of("status", "ok");
        }
        if (path.equals("/api/cases") || path.equals("/api/cases/")) {
            requireMethod(method, "GET");
            return cases.list();
        }
        Matcher m = CASE_PATH.matcher(path);
        if (m.matches()) {
            requireMethod(method, "GET");
            String id = m.group(1);
            String sub = m.group(2) == null ? "" : m.group(2);
            switch (sub) {
                case "":
                    return cases.details(id);
                case "/hearing":
                    return cases.hearing(id);
                case "/tipping-points":
                    return cases.tippingPoints(id);
                default:
                    break;
            }
        }
        if (path.equals("/api/simulations")) {
            requireMethod(method, "POST");
            JsonNode request = readBody(exchange);
            return cases.simulate(text(request, "caseId"), request.path("rounds").asInt(3));
        }
        if (path.equals("/api/strategy")) {
            requireMethod(method, "POST");
            JsonNode request = readBody(exchange);
            return cases.counterArguments(text(request, "caseId"), text(request, "text"));
        }
        throw new CaseService.NotFoundException("No such endpoint: " + method + " " + path);
    }

    private static void requireMethod(String method, String expected) {
        if (!method.equals(expected)) {
            throw new BadRequestException("Expected " + expected + ", got " + method);
        }
    }

    private static JsonNode readBody(HttpExchange exchange) {
        try {
            return JSON.readTree(exchange.getRequestBody());
        } catch (IOException e) {
            throw new BadRequestException("The request body is not valid JSON");
        }
    }

    private static String text(JsonNode request, String field) {
        String value = request.path(field).asText("");
        if (value.isBlank()) {
            throw new BadRequestException("Missing field: " + field);
        }
        return value;
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("error", code, "message", message);
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static class BadRequestException extends RuntimeException {
        BadRequestException(String message) {
            super(message);
        }
    }
}
