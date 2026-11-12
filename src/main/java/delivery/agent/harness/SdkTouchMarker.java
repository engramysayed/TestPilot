package delivery.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sidecar-owned {@code sdk-touch.marker} — authoritative for crash recovery (not stdout NDJSON).
 */
public final class SdkTouchMarker {
    public static final String FILE_NAME = "sdk-touch.marker";

    public enum State {
        /** Marker says {@code sdkTouched: false} and phase is only sidecar_started (or equivalent). */
        DEFINITELY_SAFE_TO_CREATE,
        /** Marker says {@code sdkTouched: true} — never call {@code Agent.create} again. */
        SDK_TOUCHED,
        /** Missing, corrupt, or ambiguous. */
        AMBIGUOUS
    }

    private final ObjectMapper mapper;

    public SdkTouchMarker() {
        this(new ObjectMapper());
    }

    public SdkTouchMarker(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static Path markerPath(Path taskDir) {
        return taskDir.resolve(FILE_NAME);
    }

    public State readState(Path taskDir) {
        Path file = markerPath(taskDir);
        if (!Files.isRegularFile(file)) {
            return State.AMBIGUOUS;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonNode node = mapper.readTree(raw);
            if (node == null || !node.isObject()) {
                return State.AMBIGUOUS;
            }
            JsonNode touched = node.get("sdkTouched");
            if (touched == null || !touched.isBoolean()) {
                return State.AMBIGUOUS;
            }
            if (touched.asBoolean()) {
                return State.SDK_TOUCHED;
            }
            String phase = node.has("phase") ? node.get("phase").asText("") : "";
            if ("sidecar_started".equals(phase) || phase.isBlank()) {
                return State.DEFINITELY_SAFE_TO_CREATE;
            }
            return State.AMBIGUOUS;
        } catch (IOException e) {
            return State.AMBIGUOUS;
        }
    }
}
