package delivery.portal.alerts;

/**
 * Pluggable alert channel (SPLIT_DESIGN §8, AL-1). {@code keel.alerts.channels.<id>.enabled} and
 * {@code keel.alerts.channels.<id>.min-severity}. A failing channel never blocks the others.
 */
public interface AlertChannel {
    String id();

    boolean enabled();

    Alert.Severity minSeverity();

    void send(Alert alert) throws Exception;

    /** Reasons the channel would refuse to send today (missing token, no recipients, …). Empty = ready. */
    default java.util.List<String> readinessIssues() {
        return enabled() ? java.util.List.of() : java.util.List.of("disabled");
    }
}
