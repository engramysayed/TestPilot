package delivery.authoring;

/** Job-scoped Precision engine settings (from portal props at job start). */
public record PrecisionJobConfig(
        boolean enabled,
        int maxCallsPerJob,
        boolean reuseLocatorMemory,
        String minConfidence,
        String cursorModel
) {
    public static final PrecisionJobConfig DEFAULTS = new PrecisionJobConfig(true, 50, false, "medium", "");

    public PrecisionJobConfig(boolean enabled, int maxCallsPerJob) {
        this(enabled, maxCallsPerJob, false, "medium", "");
    }

    public PrecisionJobConfig(boolean enabled, int maxCallsPerJob, boolean reuseLocatorMemory) {
        this(enabled, maxCallsPerJob, reuseLocatorMemory, "medium", "");
    }

    public PrecisionJobConfig(boolean enabled, int maxCallsPerJob, boolean reuseLocatorMemory, String minConfidence) {
        this(enabled, maxCallsPerJob, reuseLocatorMemory, minConfidence, "");
    }

    public PrecisionJobConfig {
        maxCallsPerJob = maxCallsPerJob <= 0 ? 50 : maxCallsPerJob;
        if (minConfidence == null || minConfidence.isBlank()) {
            minConfidence = "medium";
        } else {
            String normalized = minConfidence.trim().toLowerCase();
            minConfidence = "high".equals(normalized) ? "high" : "medium";
        }
        if (cursorModel == null) {
            cursorModel = "";
        } else {
            String trimmed = cursorModel.trim();
            cursorModel = "auto".equalsIgnoreCase(trimmed) ? "" : trimmed;
        }
    }

    public static PrecisionJobConfig fromPortal(boolean enabled, int maxCallsPerJob, boolean reuseLocatorMemory) {
        return new PrecisionJobConfig(enabled, maxCallsPerJob, reuseLocatorMemory, "medium", "");
    }
}
