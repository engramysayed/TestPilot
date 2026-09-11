package delivery.authoring;

/** Job-scoped Precision engine settings (from portal props at job start). */
public record PrecisionJobConfig(boolean enabled, int maxCallsPerJob) {
    public static final PrecisionJobConfig DEFAULTS = new PrecisionJobConfig(true, 50);

    public PrecisionJobConfig {
        maxCallsPerJob = maxCallsPerJob <= 0 ? 50 : maxCallsPerJob;
    }

    public static PrecisionJobConfig fromPortal(boolean enabled, int maxCallsPerJob) {
        return new PrecisionJobConfig(enabled, maxCallsPerJob);
    }
}
