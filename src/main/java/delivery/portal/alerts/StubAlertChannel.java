package delivery.portal.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * B12 stub channel: Telegram and email are not built yet (AL-1 is still open), so an enabled stub only logs
 * {@code ALERT_STUB} and keeps the message for the channel-health view and tests. Nothing leaves the machine.
 * All channels are disabled by default.
 */
public abstract class StubAlertChannel implements AlertChannel {
    private static final Logger LOG = LoggerFactory.getLogger(StubAlertChannel.class);
    private final String id;
    private final boolean enabled;
    private final Alert.Severity minSeverity;
    private final List<Alert> sent = Collections.synchronizedList(new ArrayList<>());
    private volatile Instant lastSuccess;

    protected StubAlertChannel(String id, Environment env) {
        this(id, env != null && Boolean.parseBoolean(env.getProperty("keel.alerts.channels." + id + ".enabled", "false")),
                Alert.Severity.valueOf(env == null ? "WARNING" : env.getProperty("keel.alerts.channels." + id + ".min-severity",
                        "warning").trim().toUpperCase(Locale.ROOT)));
    }

    protected StubAlertChannel(String id, boolean enabled, Alert.Severity minSeverity) {
        this.id = id;
        this.enabled = enabled;
        this.minSeverity = minSeverity;
    }

    @Override public String id() { return id; }

    @Override public boolean enabled() { return enabled; }

    @Override public Alert.Severity minSeverity() { return minSeverity; }

    @Override
    public void send(Alert alert) {
        LOG.warn("ALERT_STUB channel={} kind={} rule={} subject={} severity={}: {}", id, alert.kind(), alert.ruleId(),
                alert.subject(), alert.severity(), alert.message());
        sent.add(alert);
        lastSuccess = alert.at();
    }

    public List<Alert> sent() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    public Instant lastSuccess() {
        return lastSuccess;
    }
}
