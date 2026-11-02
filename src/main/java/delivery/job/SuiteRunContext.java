package delivery.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Thread-local suite run metadata for AE execute/prove (Phase 3).
 * Set by schedule claim / portal suite run start; cleared in finally.
 */
public final class SuiteRunContext {
    public static final String TRIGGER_SCHEDULE = "schedule";
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_MONITOR = "monitor";

    private static final ThreadLocal<SuiteRunContext> CURRENT = new ThreadLocal<>();

    private final String projectId;
    private final String suiteId;
    private final String tag;
    private final List<String> tcIds;
    private final String trigger;
    private final String alertChannelId;
    private final String monitorTarget;

    public SuiteRunContext(
            String projectId,
            String suiteId,
            String tag,
            List<String> tcIds,
            String trigger,
            String alertChannelId,
            String monitorTarget
    ) {
        this.projectId = projectId;
        this.suiteId = blankToNull(suiteId);
        this.tag = blankToNull(tag);
        this.tcIds = tcIds == null ? List.of() : List.copyOf(compact(tcIds));
        this.trigger = normalizeTrigger(trigger);
        this.alertChannelId = blankToNull(alertChannelId);
        this.monitorTarget = blankToNull(monitorTarget);
    }

    public static SuiteRunContext current() {
        return CURRENT.get();
    }

    public static void activate(SuiteRunContext ctx) {
        if (ctx == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(ctx);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Activate from system properties when set (keel.suite.*); no-op if none present. */
    public static boolean activateFromSystemProperties() {
        String suiteId = System.getProperty("keel.suite.suiteId", System.getProperty("keel.suite.id", ""));
        String tag = System.getProperty("keel.suite.tag", "");
        String tcIdsRaw = System.getProperty("keel.suite.tcIds", "");
        String trigger = System.getProperty("keel.suite.trigger", "");
        String alert = System.getProperty("keel.suite.alertChannelId", "");
        String monitor = System.getProperty("keel.suite.monitorTarget", "");
        String projectId = System.getProperty("keel.suite.projectId", "");
        if (isBlank(suiteId) && isBlank(tag) && isBlank(tcIdsRaw) && isBlank(trigger)) {
            return false;
        }
        List<String> tcIds = new ArrayList<>();
        if (!isBlank(tcIdsRaw)) {
            for (String p : tcIdsRaw.split("[,;]")) {
                if (!isBlank(p)) {
                    tcIds.add(p.trim());
                }
            }
        }
        activate(new SuiteRunContext(projectId, suiteId, tag, tcIds, trigger, alert, monitor));
        return true;
    }

    public String projectId() {
        return projectId;
    }

    public String suiteId() {
        return suiteId;
    }

    public String tag() {
        return tag;
    }

    public List<String> tcIds() {
        return tcIds;
    }

    public String trigger() {
        return trigger;
    }

    public String alertChannelId() {
        return alertChannelId;
    }

    public String monitorTarget() {
        return monitorTarget;
    }

    public boolean isMonitor() {
        return TRIGGER_MONITOR.equals(trigger);
    }

    public boolean hasFilter() {
        return suiteId != null || tag != null || !tcIds.isEmpty();
    }

    private static String normalizeTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return TRIGGER_MANUAL;
        }
        String t = trigger.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case TRIGGER_SCHEDULE, TRIGGER_MONITOR, TRIGGER_MANUAL -> t;
            default -> TRIGGER_MANUAL;
        };
    }

    private static List<String> compact(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
        return out;
    }

    private static String blankToNull(String v) {
        return isBlank(v) ? null : v.trim();
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
