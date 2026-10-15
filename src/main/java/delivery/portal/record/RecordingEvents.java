package delivery.portal.record;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** Parses the JSON blob the page hook returns. */
final class RecordingEvents {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Snapshot(String generation, List<RecordingLog.Event> events) {
    }

    private RecordingEvents() {
    }

    static Snapshot parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            String gen = root.path("gen").asText("");
            List<RecordingLog.Event> events = new ArrayList<>();
            JsonNode steps = root.path("steps");
            if (steps.isArray()) {
                for (JsonNode step : steps) {
                    events.add(new RecordingLog.Event(
                            step.path("k").asText(""),
                            step.path("label").asText(""),
                            step.path("value").asText(""),
                            step.path("secret").asBoolean(false),
                            step.path("s").asText(""),
                            step.path("v").asText("")));
                }
            }
            return new Snapshot(gen, List.copyOf(events));
        } catch (Exception e) {
            return null;
        }
    }
}
