package ac.qbaf.legalsim.llm;

import ac.qbaf.legalsim.agents.DefenseAgent;
import ac.qbaf.legalsim.agents.JudgeAgent;
import ac.qbaf.legalsim.agents.JudgeAgent.Assessment;
import ac.qbaf.legalsim.agents.PartyAgent.Draft;
import ac.qbaf.legalsim.agents.PartyAgent.Move;
import ac.qbaf.legalsim.agents.ProsecutorAgent;
import ac.qbaf.legalsim.data.LegalCase;
import ac.qbaf.legalsim.simulation.TrialSimulation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ollama מול שרת מזויף שמדבר באותו פרוטוקול (POST /api/chat). */
class OllamaTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final List<JsonNode> requests = new ArrayList<>();

    /** answer: מקבל את הבקשה ומחזיר {status, body}. */
    private String start(Function<JsonNode, Object[]> answer) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/chat", exchange -> {
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            synchronized (requests) {
                requests.add(request);
            }
            Object[] reply = answer.apply(request);
            send(exchange, (Integer) reply[0], (String) reply[1]);
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Object[] content(String json) {
        try {
            return new Object[]{200, JSON.writeValueAsString(Map.of(
                    "message", Map.of("role", "assistant", "content", json), "done", true))};
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsSchemaContextAndTemperatureAndParsesTheAnswer() throws IOException {
        String host = start(request -> content("{\"arguments\":[{\"text\":\"one\",\"targets\":[\"C\"],\"reuse\":\"\"},{\"text\":\"two\",\"targets\":[\"P1\",\"C\"],\"reuse\":\"\"}]}"));
        Ollama ollama = new Ollama(host, "gemma4:12b", false);

        Move move = ollama.structured("system", "x".repeat(40_000), Move.class, 0.7);

        assertEquals(List.of("one", "two"), move.arguments().stream().map(Draft::text).toList());
        assertEquals(List.of("P1", "C"), move.arguments().get(1).targets());
        JsonNode request = requests.get(0);
        assertEquals("gemma4:12b", request.path("model").asText());
        assertFalse(request.path("stream").asBoolean());
        assertFalse(request.path("think").asBoolean());
        assertEquals("system", request.path("messages").get(0).path("role").asText());
        assertEquals(0.7, request.path("options").path("temperature").asDouble(), 1e-9);
        // 40000 תווים ≈ 13.3K טוקנים + 4K לתשובה → 32K
        assertEquals(32768, request.path("options").path("num_ctx").asInt());
        JsonNode schema = request.path("format");
        assertEquals("object", schema.path("type").asText());
        assertEquals("array", schema.path("properties").path("arguments").path("type").asText());
        JsonNode item = schema.path("properties").path("arguments").path("items");
        assertEquals("object", item.path("type").asText());
        assertTrue(item.path("properties").path("text").path("description").asText().contains("self-contained"));
        assertEquals("array", item.path("properties").path("targets").path("type").asText());
        assertTrue(item.path("properties").path("targets").path("description").asText().contains("central claim"));
        assertEquals(List.of("text", "targets", "reuse"), JSON.convertValue(item.path("required"), List.class));
    }

    @Test
    void contextGrowsWithThePromptWithinLimits() {
        assertEquals(8192, Ollama.contextFor(100));
        assertEquals(16384, Ollama.contextFor(14_000));
        assertEquals(131072, Ollama.contextFor(10_000_000));
    }

    @Test
    void retriesInvalidJsonThenSucceeds() throws IOException {
        int[] calls = {0};
        String host = start(request -> ++calls[0] == 1 ? content("{\"arguments\": [{\"text\": \"cut off") : content("{\"arguments\":[{\"text\":\"ok\",\"targets\":[\"C\"],\"reuse\":\"\"}]}"));

        Move move = new Ollama(host, "m", false).structured("s", "u", Move.class, 0.0);

        assertEquals("ok", move.arguments().get(0).text());
        assertEquals(2, calls[0]);
    }

    @Test
    void explainsAMissingModelAndAStoppedServer() throws IOException {
        String host = start(request -> new Object[]{404, "{\"error\":\"model 'gemma4:12b' not found\"}"});

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> new Ollama(host, "gemma4:12b", false).structured("s", "u", Move.class, 0.0));
        assertTrue(missing.getMessage().contains("ollama pull gemma4:12b"));

        server.stop(0);
        IllegalStateException stopped = assertThrows(IllegalStateException.class,
                () -> new Ollama(host, "gemma4:12b", false).structured("s", "u", Move.class, 0.0));
        assertTrue(stopped.getMessage().contains("not running"));
    }

    /** הסימולציה המלאה דרך Ollama: הסכמות של Move ו-Assessment עוברות בפרוטוקול ונקראות חזרה. */
    @Test
    void fullSimulationRunsThroughOllama() throws IOException {
        Pattern argumentLine = Pattern.compile("^([PD]\\d+) \\((applicant|government)\\)", Pattern.MULTILINE);
        String host = start(request -> {
            JsonNode properties = request.path("format").path("properties");
            String user = request.path("messages").get(1).path("content").asText();
            if (properties.has("arguments")) {
                boolean applicant = user.contains("You speak for the applicant");
                return content(applicant ? "{\"arguments\":[{\"text\":\"press freedom\",\"targets\":[\"C\"],\"reuse\":\"\"},{\"text\":\"fine too heavy\",\"targets\":[\"C\"],\"reuse\":\"\"}]}"
                        : "{\"arguments\":[{\"text\":\"insulting words\",\"targets\":[\"P1\"],\"reuse\":\"\"}]}");
            }
            StringBuilder scores = new StringBuilder("{\"scores\":[");
            Matcher m = argumentLine.matcher(user.substring(user.indexOf("<arguments to assess>")));
            boolean first = true;
            while (m.find()) {
                scores.append(first ? "" : ",").append("{\"argumentId\":\"").append(m.group(1))
                        .append("\",\"category\":\"public_interest\",\"strength\":0.5,\"duplicateOf\":\"\",\"rationale\":\"r\"}");
                first = false;
            }
            return content(scores.append("]}").toString());
        });
        Ollama ollama = new Ollama(host, "gemma4:12b", false);
        TrialSimulation simulation = new TrialSimulation(
                new ProsecutorAgent(m -> ollama.structured("p", m, Move.class, Llm.CREATIVE)),
                new DefenseAgent(m -> ollama.structured("d", m, Move.class, Llm.CREATIVE)),
                new JudgeAgent(m -> ollama.structured("j", m, Assessment.class, Llm.PRECISE)));
        LegalCase legalCase = new LegalCase("001-1", "X v. Y", "facts", List.of("a"), List.of("g"), true,
                LegalCase.VIOLATION);

        TrialSimulation.Result result = simulation.run(legalCase, 2);

        // 2 סבבים × (תביעה + הגנה + שופט)
        assertEquals(6, requests.size());
        assertEquals(6, result.transcript().size());
        assertEquals(LegalCase.VIOLATION, result.predicted());
        // P1 מותקף פעמיים (0.5, 0.5) → 0.125; תומכי C: 0.125, 0.5, 0.5, 0.5 → σ(C) = 0.9453125
        assertEquals(0.9453125, result.claimStrength(), 1e-9);
        assertEquals("P1", result.transcript().get(2).edges().get(0).to());
        JsonNode judgeSchema = requests.get(2).path("format").path("properties").path("scores").path("items");
        assertEquals("number", judgeSchema.path("properties").path("strength").path("type").asText());
        assertEquals(List.of("argumentId", "category", "strength", "duplicateOf", "rationale"),
                JSON.convertValue(judgeSchema.path("required"), List.class));
    }
}
