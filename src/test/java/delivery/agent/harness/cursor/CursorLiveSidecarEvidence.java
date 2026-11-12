package delivery.agent.harness.cursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only: capture sidecar NDJSON for live verification reports. */
final class CursorLiveSidecarEvidence {
    private CursorLiveSidecarEvidence() {
    }

    static List<JsonNode> parseNdjsonLines(List<String> rawLines) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> out = new ArrayList<>();
        for (String line : rawLines) {
            if (line == null) {
                continue;
            }
            line = line.trim();
            if (!line.startsWith("{")) {
                continue;
            }
            out.add(mapper.readTree(line));
        }
        return out;
    }

    static void writeEvidence(Path out, Map<String, Object> body) throws Exception {
        Files.createDirectories(out.getParent() == null ? Path.of(".") : out.getParent());
        ObjectMapper mapper = new ObjectMapper();
        Files.writeString(out, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body), StandardCharsets.UTF_8);
    }

    static boolean ndjsonContainsCancelLifecycle(List<JsonNode> events) {
        for (JsonNode n : events) {
            if ("lifecycle".equals(n.path("type").asText())
                    && n.path("phase").asText("").contains("cancel")) {
                return true;
            }
        }
        return false;
    }

    static String resultStatus(List<JsonNode> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            JsonNode n = events.get(i);
            if ("result".equals(n.path("type").asText())) {
                return n.path("status").asText("");
            }
        }
        return "";
    }

    static Map<String, Object> summarizeNdjson(List<JsonNode> events) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lineCount", events.size());
        m.put("resultStatus", resultStatus(events));
        m.put("cancelLifecycleSeen", ndjsonContainsCancelLifecycle(events));
        long pollEvents = events.stream()
                .filter(n -> "sdk.event".equals(n.path("type").asText())
                        && "reconcile.poll".equals(n.path("stream").asText()))
                .count();
        m.put("reconcilePollEvents", pollEvents);
        return m;
    }
}
