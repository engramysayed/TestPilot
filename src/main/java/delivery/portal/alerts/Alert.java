package delivery.portal.alerts;

import java.time.Instant;

/** One alert notification (SPLIT_DESIGN §8). {@code kind}: RAISED, REPEAT (still firing after the mute window) or RESOLVED. */
public record Alert(String ruleId, String subject, Severity severity, Kind kind, String message, Instant at) {
    public enum Severity { WARNING, CRITICAL }

    public enum Kind { RAISED, REPEAT, RESOLVED }

    public String dedupKey() {
        return ruleId + "|" + subject;
    }
}
