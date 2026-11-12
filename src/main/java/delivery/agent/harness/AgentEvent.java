package delivery.agent.harness;

import java.time.Instant;
import java.util.Map;

/** One line in {@code events.jsonl}. */
public record AgentEvent(
        Instant at,
        String type,
        String message,
        Map<String, Object> details
) {
    public AgentEvent {
        at = at == null ? Instant.now() : at;
        type = type == null ? "" : type;
        message = message == null ? "" : message;
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
