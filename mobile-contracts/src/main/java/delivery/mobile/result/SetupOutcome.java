package delivery.mobile.result;

/** Auth/start-state/prerequisite setup outcome for a case (M2-07). */
public enum SetupOutcome {
    NOT_REQUIRED,
    PASSED,
    FAILED,
    BLOCKED,
    SKIPPED,
    UNKNOWN;

    public static SetupOutcome parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NOT_REQUIRED;
        }
        return SetupOutcome.valueOf(raw.trim().toUpperCase());
    }
}
