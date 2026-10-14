package delivery.job;

/**
 * Per-intent retry budget. TEST and RECOVERY failures consume {@code maxTestRetries};
 * INFRA failures consume a separate {@code maxInfraRetries} cap (with backoff) so environment noise
 * does not burn the test's budget while the loop stays bounded. CONFIG failures are never retried.
 */
public final class RetryBudget {
    /** Injectable sleep so tests do not wait for real backoff. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;

        Sleeper REAL = Thread::sleep;
    }

    /** Coordinator decision: 2 infra retries, backoff 2 s then 5 s. */
    public static final long[] DEFAULT_INFRA_BACKOFF_MS = {2_000L, 5_000L};

    private final int maxTestRetries;
    private final int maxInfraRetries;
    private final long[] infraBackoffMs;
    private final Sleeper sleeper;
    private int testFailures;
    private int recoveries;
    private int infraFailures;
    private RetryCategory lastDenied;

    public RetryBudget(int maxTestRetries, int maxInfraRetries) {
        this(maxTestRetries, maxInfraRetries, new long[0], ms -> { });
    }

    public RetryBudget(int maxTestRetries, int maxInfraRetries, long[] infraBackoffMs, Sleeper sleeper) {
        this.maxTestRetries = Math.max(0, maxTestRetries);
        this.maxInfraRetries = Math.max(0, maxInfraRetries);
        this.infraBackoffMs = infraBackoffMs == null ? new long[0] : infraBackoffMs.clone();
        this.sleeper = sleeper == null ? Sleeper.REAL : sleeper;
    }

    /** Backoff before infra retry {@code n} (1-based); last value repeats, 0 when none configured. */
    public long backoffFor(int n) {
        if (infraBackoffMs.length == 0 || n <= 0) {
            return 0L;
        }
        return infraBackoffMs[Math.min(n, infraBackoffMs.length) - 1];
    }

    /** Record a failed attempt; returns true when another attempt is allowed (sleeps for INFRA backoff). */
    public boolean allowRetry(RetryCategory category) {
        RetryCategory c = category == null ? RetryCategory.TEST : category;
        if (c == RetryCategory.CONFIG) {
            lastDenied = c;
            return false;
        }
        if (c == RetryCategory.INFRA) {
            if (infraFailures >= maxInfraRetries) {
                lastDenied = c;
                return false;
            }
            infraFailures++;
            long wait = backoffFor(infraFailures);
            if (wait > 0) {
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    lastDenied = c;
                    return false;
                }
            }
            return true;
        }
        if (testFailures + recoveries >= maxTestRetries) {
            lastDenied = c;
            return false;
        }
        if (c == RetryCategory.RECOVERY) {
            recoveries++;
        } else {
            testFailures++;
        }
        return true;
    }

    /** Retries charged to the test budget (TEST + RECOVERY) — the historical {@code retriesUsed}. */
    public int testRetriesUsed() {
        return testFailures + recoveries;
    }

    /** Genuine test-category failures that were retried (drives FLAKY). */
    public int testFailures() {
        return testFailures;
    }

    public int infraRetriesUsed() {
        return infraFailures;
    }

    /** Category that stopped the loop, or null if the loop ended on success. */
    public RetryCategory exhaustedOn() {
        return lastDenied;
    }
}
