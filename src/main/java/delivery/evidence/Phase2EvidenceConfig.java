package delivery.evidence;

/**
 * Feature flags for Phase 2 failure replay / heal propose-only / flaky emit.
 * Safe defaults: capture + propose-only + flaky emit ON; network/console best-effort.
 */
public final class Phase2EvidenceConfig {
    public static final String PROP_FAILURE_EVIDENCE = "keel.phase2.failure.evidence";
    public static final String PROP_HEAL_PROPOSE_ONLY = "keel.phase2.heal.proposeOnly";
    public static final String PROP_FLAKY_EMIT = "keel.phase2.flaky.emit";
    public static final String PROP_DIAG_ATTACH = "keel.phase2.diag.attach";

    private Phase2EvidenceConfig() {
    }

    /** Write failure/{stepIndex}/ screen+network+console (+ meta). Default true. */
    public static boolean failureEvidenceEnabled() {
        return bool(PROP_FAILURE_EVIDENCE, true);
    }

    /**
     * Emit heal-proposals JSON; never rewrite generated Java / skip workbook auto-apply.
     * Default true (Phase 2 DoD).
     */
    public static boolean healProposeOnly() {
        return bool(PROP_HEAL_PROPOSE_ONLY, true);
    }

    /** Append flaky-tags.json signals. Default true. */
    public static boolean flakyEmitEnabled() {
        return bool(PROP_FLAKY_EMIT, true);
    }

    /** Attach CDP network/console buffer for prove/execute. Default true when failure evidence on. */
    public static boolean diagAttachEnabled() {
        if (System.getProperty(PROP_DIAG_ATTACH) != null) {
            return bool(PROP_DIAG_ATTACH, true);
        }
        return failureEvidenceEnabled();
    }

    private static boolean bool(String prop, boolean def) {
        String raw = System.getProperty(prop);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
