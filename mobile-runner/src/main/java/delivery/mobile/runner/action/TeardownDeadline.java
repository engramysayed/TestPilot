package delivery.mobile.runner.action;

/**
 * Teardown budget is separate from and not consumed by the case/job deadline (arch §4 / M1-06).
 * Default 30s — aligns with {@code delivery.mobile.teardown-deadline-seconds}.
 */
public final class TeardownDeadline {
    private final long budgetMs;
    private final long caseDeadlineEpochMs;
    private final long teardownStartEpochMs;

    public TeardownDeadline(long budgetMs, long caseDeadlineEpochMs, long teardownStartEpochMs) {
        if (budgetMs <= 0) throw new IllegalArgumentException("budgetMs > 0");
        this.budgetMs = budgetMs;
        this.caseDeadlineEpochMs = caseDeadlineEpochMs;
        this.teardownStartEpochMs = teardownStartEpochMs;
    }

    public static TeardownDeadline defaults(long nowEpochMs, long caseDeadlineEpochMs) {
        return new TeardownDeadline(30_000L, caseDeadlineEpochMs, nowEpochMs);
    }

    public long budgetMs() { return budgetMs; }

    /** Absolute epoch when teardown must finish — independent of case deadline. */
    public long teardownDeadlineEpochMs() {
        return teardownStartEpochMs + budgetMs;
    }

    /** Even if case deadline is already past, full teardown budget remains. */
    public boolean caseExhaustedButTeardownOpen(long nowEpochMs) {
        return nowEpochMs >= caseDeadlineEpochMs && nowEpochMs < teardownDeadlineEpochMs();
    }

    public boolean teardownExceeded(long nowEpochMs) {
        return nowEpochMs >= teardownDeadlineEpochMs();
    }
}
