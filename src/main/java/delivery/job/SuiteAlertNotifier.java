package delivery.job;

import delivery.portal.alerts.Alert;
import delivery.portal.alerts.AlertChannel;
import utils.LogsManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Phase 3 AE: fire suite fail/flake via bound {@link AlertChannel} (reuse existing stack).
 * Unbound suite / missing channel → log only. Portal may {@link #registerChannels} at startup.
 */
public final class SuiteAlertNotifier {
    public static final String RULE_SUITE_RUN_FAILED = "SUITE_RUN_FAILED";
    public static final String RULE_SUITE_RUN_FLAKY = "SUITE_RUN_FLAKY";

    private static final CopyOnWriteArrayList<AlertChannel> CHANNELS = new CopyOnWriteArrayList<>();
    /** Portal hook: (projectId, alert) → each project member's saved account alert settings. */
    private static volatile BiConsumer<String, Alert> memberAlerts;

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

    public static void registerMemberAlerts(BiConsumer<String, Alert> hook) {
        memberAlerts = hook;
    }

    public static List<AlertChannel> channels() {
        return List.copyOf(CHANNELS);
    }

    public static final String RULE_SUITE_RUN_ERROR = "SUITE_RUN_ERROR";
    public static final String RULE_SUITE_SCHEDULE_FAILED = "SUITE_SCHEDULE_FAILED";

    /** A suite run that crashed / was refused before producing a result (no {@link #notifyOutcome} call). */
    public static void notifyError(SuiteRunContext ctx, String ruleId, String message) {
        if (ctx == null) {
            return;
        }
        String subject = "suite:" + (ctx.suiteId() == null ? "unknown" : ctx.suiteId());
        send(ctx, ctx.alertChannelId(), ruleId, subject, Alert.Severity.CRITICAL,
                "Suite run did not complete (" + ctx.trigger() + "): " + (message == null ? "" : message));
    }

    /** Notify from active {@link SuiteRunContext} after an execute/prove outcome. */
    public static void notifyOutcome(int passed, int todo, int flaky, String message) {
        SuiteRunContext ctx = SuiteRunContext.current();
        if (ctx == null) {
            return;
        }
        // Any failing case alerts (a regression with 1 of 50 failing used to be silent: only all-fail alerted).
        // All failed → CRITICAL; some failed → WARNING, so channels can filter by severity.
        boolean failed = todo > 0;
        boolean flake = flaky > 0;
        if (!failed && !flake) {
            return;
        }
        String channelId = ctx.alertChannelId();
        String subject = "suite:" + (ctx.suiteId() == null ? "unknown" : ctx.suiteId());
        if (failed) {
            boolean allFailed = passed == 0;
            send(ctx, channelId, RULE_SUITE_RUN_FAILED, subject,
                    allFailed ? Alert.Severity.CRITICAL : Alert.Severity.WARNING,
                    (allFailed ? "Suite run failed: all " + todo + " case(s) failed"
                            : "Suite run: " + todo + " of " + (passed + todo) + " case(s) failed")
                            + " (" + ctx.trigger() + ")"
                            + (message == null || message.isBlank() ? "" : (" — " + message)));
        }
        if (flake) {
            send(ctx, channelId, RULE_SUITE_RUN_FLAKY, subject, Alert.Severity.WARNING,
                    "Suite run flaky count=" + flaky
                            + (ctx.suiteId() == null ? "" : (" suiteId=" + ctx.suiteId())));
        }
    }

    public static void send(String channelId, String ruleId, String subject, Alert.Severity severity, String message) {
        send(SuiteRunContext.current(), channelId, ruleId, subject, severity, message);
    }

    private static void send(SuiteRunContext ctx, String channelId, String ruleId, String subject,
                             Alert.Severity severity, String message) {
        Alert alert = new Alert(ruleId, subject, severity, Alert.Kind.RAISED, message, Instant.now());
        Alert.Severity suiteMin = suiteMin(ctx);
        notifyMembers(ctx, alert, suiteMin);
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
        // Per-suite override: suite.alertMinSeverity can RAISE the bar above the channel default.
        // Never lowers: max(channel.minSeverity, suite.alertMinSeverity).
        Alert.Severity floor = channel.minSeverity();
        if (suiteMin != null && suiteMin.compareTo(floor) > 0) {
            floor = suiteMin;
        }
        if (alert.severity().compareTo(floor) < 0) {
            LogsManager.info("SUITE_ALERT: below minSeverity channel=" + channelId + " rule=" + ruleId
                    + " floor=" + floor);
            return;
        }
        try {
            channel.send(alert);
            LogsManager.info("SUITE_ALERT: sent channel=" + channelId + " rule=" + ruleId);
        } catch (Exception e) {
            LogsManager.warn("SUITE_ALERT: send failed channel=" + channelId + " " + e.getMessage());
        }
    }

    private static Alert.Severity suiteMin(SuiteRunContext ctx) {
        if (ctx == null || ctx.alertMinSeverity() == null || ctx.alertMinSeverity().isBlank()) {
            return null;
        }
        try {
            return Alert.Severity.valueOf(ctx.alertMinSeverity().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null; // unknown label, no per-suite floor
        }
    }

    /** Members get the alert whether or not the suite is bound to a server channel; suite floor still applies. */
    private static void notifyMembers(SuiteRunContext ctx, Alert alert, Alert.Severity suiteMin) {
        BiConsumer<String, Alert> hook = memberAlerts;
        if (hook == null || ctx == null || ctx.projectId() == null || ctx.projectId().isBlank()) {
            return;
        }
        if (suiteMin != null && alert.severity().compareTo(suiteMin) < 0) {
            return;
        }
        try {
            hook.accept(ctx.projectId(), alert);
        } catch (Exception e) {
            LogsManager.warn("SUITE_ALERT: member alerts failed rule=" + alert.ruleId() + " " + e.getMessage());
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
