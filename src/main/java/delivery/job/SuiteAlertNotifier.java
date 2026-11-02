package delivery.job;

import delivery.portal.alerts.Alert;
import delivery.portal.alerts.AlertChannel;
import utils.LogsManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Phase 3 AE: fire suite fail/flake via bound {@link AlertChannel} (reuse existing stack).
 * Unbound suite / missing channel → log only. Portal may {@link #registerChannels} at startup.
 */
public final class SuiteAlertNotifier {
    public static final String RULE_SUITE_RUN_FAILED = "SUITE_RUN_FAILED";
    public static final String RULE_SUITE_RUN_FLAKY = "SUITE_RUN_FLAKY";

    private static final CopyOnWriteArrayList<AlertChannel> CHANNELS = new CopyOnWriteArrayList<>();

    private SuiteAlertNotifier() {
    }

    public static void registerChannels(List<AlertChannel> channels) {
        CHANNELS.clear();
        if (channels != null) {
            CHANNELS.addAll(channels);
        }
    }

    public static void clearChannels() {
        CHANNELS.clear();
    }

    public static List<AlertChannel> channels() {
        return List.copyOf(CHANNELS);
    }

    /** Notify from active {@link SuiteRunContext} after an execute/prove outcome. */
    public static void notifyOutcome(int passed, int todo, int flaky, String message) {
        SuiteRunContext ctx = SuiteRunContext.current();
        if (ctx == null) {
            return;
        }
        boolean failed = passed == 0 && todo > 0;
        boolean flake = flaky > 0;
        if (!failed && !flake) {
            return;
        }
        String channelId = ctx.alertChannelId();
        String subject = "suite:" + (ctx.suiteId() == null ? "unknown" : ctx.suiteId());
        if (failed) {
            send(channelId, RULE_SUITE_RUN_FAILED, subject, Alert.Severity.CRITICAL,
                    "Suite run failed passed=" + passed + " todo=" + todo
                            + (message == null || message.isBlank() ? "" : (" — " + message)));
        }
        if (flake) {
            send(channelId, RULE_SUITE_RUN_FLAKY, subject, Alert.Severity.WARNING,
                    "Suite run flaky count=" + flaky
                            + (ctx.suiteId() == null ? "" : (" suiteId=" + ctx.suiteId())));
        }
    }

    public static void send(String channelId, String ruleId, String subject, Alert.Severity severity, String message) {
        if (channelId == null || channelId.isBlank()) {
            LogsManager.info("SUITE_ALERT: unbound channel; log only rule=" + ruleId + " " + message);
            return;
        }
        AlertChannel channel = find(channelId);
        if (channel == null) {
            LogsManager.info("SUITE_ALERT: channel not registered id=" + channelId + " rule=" + ruleId
                    + " — log only: " + message);
            return;
        }
        if (!channel.enabled()) {
            LogsManager.info("SUITE_ALERT: channel disabled id=" + channelId + " rule=" + ruleId);
            return;
        }
        Alert alert = new Alert(ruleId, subject, severity, Alert.Kind.RAISED, message, Instant.now());
        if (alert.severity().compareTo(channel.minSeverity()) < 0) {
            LogsManager.info("SUITE_ALERT: below minSeverity channel=" + channelId + " rule=" + ruleId);
            return;
        }
        try {
            channel.send(alert);
            LogsManager.info("SUITE_ALERT: sent channel=" + channelId + " rule=" + ruleId);
        } catch (Exception e) {
            LogsManager.warn("SUITE_ALERT: send failed channel=" + channelId + " " + e.getMessage());
        }
    }

    static AlertChannel find(String channelId) {
        String want = channelId.trim().toLowerCase(Locale.ROOT);
        for (AlertChannel c : CHANNELS) {
            if (c != null && c.id() != null && want.equals(c.id().trim().toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }

    /** Test helper: snapshot copy. */
    static List<String> registeredIds() {
        List<String> ids = new ArrayList<>();
        for (AlertChannel c : CHANNELS) {
            if (c != null && c.id() != null) {
                ids.add(c.id());
            }
        }
        return ids;
    }
}
