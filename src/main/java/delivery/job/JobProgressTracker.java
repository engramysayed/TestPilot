package delivery.job;

import delivery.ir.TcDraftStatus;

/**
 * Live job progress mirrored into the portal while a conversion runs.
 */
public class JobProgressTracker {
    private volatile String message = "";
    private volatile int current;
    private volatile int total;
    private volatile int passed;
    private volatile int todo;
    private volatile int partial;
    private volatile int flaky;
    private volatile int infraError;
    private volatile int reusedFlaky;
    private final java.util.List<RetryVerdict> verdicts = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void update(int current, int total, String message) {
        this.current = current;
        this.total = total;
        this.message = message == null ? "" : message;
    }

    /** Prefer a pre-reserved job total (e.g. prove + design-compare) over the phase size. */
    public int effectiveTotal(int phaseTotal) {
        return Math.max(this.total, phaseTotal);
    }

    /** Update counters after each TC finishes prove (or dry-run). */
    public void recordOutcome(TcDraftStatus status) {
        if (status == null) {
            return;
        }
        switch (status) {
            case PASSED, REUSED -> passed++;
            case PARTIAL -> {
                partial++;
                todo++; // portal "TODO" includes blocked mid-case
            }
            case TODO -> todo++;
        }
    }

    /** Honest retry verdict per executed TC (FLAKY / INFRA_ERROR surface alongside passed/todo). */
    public void recordVerdict(RetryVerdict verdict) {
        if (verdict == null) {
            return;
        }
        verdicts.add(verdict);
        if (verdict == RetryVerdict.FLAKY) {
            flaky++;
        } else if (verdict == RetryVerdict.INFRA_ERROR) {
            infraError++;
        }
    }

    /** A reused TC whose prior verdict was FLAKY (reuse allowed but flagged). */
    public void recordReusedFlaky() {
        reusedFlaky++;
    }

    public int healed() {
        return health().healed();
    }

    public int configError() {
        return health().configError();
    }

    public int reusedFlaky() {
        return reusedFlaky;
    }

    public int flaky() {
        return flaky;
    }

    public int infraError() {
        return infraError;
    }

    public RunHealth health() {
        return RunHealth.of(verdicts, reusedFlaky);
    }

    /** For dry-run / emit final without TcDraftStatus. */
    public void setScores(int passed, int todo) {
        this.passed = Math.max(0, passed);
        this.todo = Math.max(0, todo);
        this.partial = 0;
    }

    public String message() {
        return message;
    }

    public int current() {
        return current;
    }

    public int total() {
        return total;
    }

    public int passed() {
        return passed;
    }

    public int todo() {
        return todo;
    }

    public int partial() {
        return partial;
    }
}
