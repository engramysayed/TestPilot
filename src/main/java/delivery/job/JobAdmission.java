package delivery.job;

/**
 * Tenant and installation admission limits. Rejection happens before a QUEUED row is stored.
 */
public final class JobAdmission {
    public static final int DEFAULT_MAX_QUEUED_PER_TENANT = 20;
    public static final int DEFAULT_MAX_RUNNING_PER_INSTALL = 8;

    public record Limits(int maxQueuedPerTenant, int maxRunningPerInstall) {
        public static Limits defaults() {
            return fromEnvironment();
        }

        public static Limits fromEnvironment() {
            int queued = intProp("delivery.jobs.max-queued-per-tenant", "DELIVERY_JOBS_MAX_QUEUED_PER_TENANT",
                    DEFAULT_MAX_QUEUED_PER_TENANT);
            int running = intProp("delivery.jobs.max-running-per-install", "DELIVERY_JOBS_MAX_RUNNING_PER_INSTALL",
                    DEFAULT_MAX_RUNNING_PER_INSTALL);
            return new Limits(Math.max(0, queued), Math.max(0, running));
        }

        private static int intProp(String prop, String env, int fallback) {
            String raw = System.getProperty(prop, "");
            if (raw == null || raw.isBlank()) {
                raw = System.getenv().getOrDefault(env, "");
            }
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    public record Decision(boolean admitted, String code, String reason) {
        public static Decision allow() {
            return new Decision(true, "ADMITTED", "admitted");
        }
    }

    private JobAdmission() {
    }

    public static Decision inspect(int queuedOrActiveForTenant, int runningForInstall, Limits limits) {
        Limits lim = limits == null ? Limits.defaults() : limits;
        if (lim.maxQueuedPerTenant() <= 0 || lim.maxRunningPerInstall() <= 0) {
            return new Decision(false, "QUEUE_SATURATED", "job admission limits are fail-closed");
        }
        if (queuedOrActiveForTenant >= lim.maxQueuedPerTenant()) {
            return new Decision(false, "QUEUE_SATURATED",
                    "tenant already has " + queuedOrActiveForTenant + " active jobs");
        }
        if (runningForInstall >= lim.maxRunningPerInstall()) {
            return new Decision(false, "INSTALL_BUSY",
                    "installation already has " + runningForInstall + " running jobs");
        }
        return Decision.allow();
    }

    public static void require(int queuedOrActiveForTenant, int runningForInstall, Limits limits) {
        Decision d = inspect(queuedOrActiveForTenant, runningForInstall, limits);
        if (!d.admitted()) {
            throw new JobAdmissionException(d.code(), d.reason());
        }
    }
}
