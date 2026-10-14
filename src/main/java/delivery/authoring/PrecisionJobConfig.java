package delivery.authoring;

/** Job-scoped Precision engine settings (from portal props at job start). */
public record PrecisionJobConfig(boolean enabled, int maxCallsPerJob, boolean reuseLocatorMemory) {
    public static final PrecisionJobConfig DEFAULTS = new PrecisionJobConfig(true, 50, false);

    public PrecisionJobConfig(boolean enabled, int maxCallsPerJob) {
        this(enabled, maxCallsPerJob, false);
    }

    public PrecisionJobConfig {
        maxCallsPerJob = maxCallsPerJob <= 0 ? 50 : maxCallsPerJob;
    }

    public static PrecisionJobConfig fromPortal(boolean enabled, int maxCallsPerJob, boolean reuseLocatorMemory) {
        return new PrecisionJobConfig(enabled, maxCallsPerJob, reuseLocatorMemory);
    }
}
