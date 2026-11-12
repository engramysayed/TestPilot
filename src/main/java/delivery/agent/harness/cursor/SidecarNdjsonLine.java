package delivery.agent.harness.cursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/** One NDJSON line from the cursor agent sidecar. */
public record SidecarNdjsonLine(String type, JsonNode rawNode) {

    public JsonNode raw() {
        return rawNode;
    }

    public static SidecarNdjsonLine parse(String line, ObjectMapper mapper) throws java.io.IOException {
        JsonNode node = mapper.readTree(line);
        String type = node.has("type") ? node.get("type").asText("") : "";
        return new SidecarNdjsonLine(type, node);
    }

    public String text(String field) {
        return rawNode != null && rawNode.has(field) ? rawNode.get(field).asText("") : "";
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> payloadMap() {
        if (rawNode == null || !rawNode.has("payload")) {
            return Map.of();
        }
        return new ObjectMapper().convertValue(rawNode.get("payload"), Map.class);
    }
}
