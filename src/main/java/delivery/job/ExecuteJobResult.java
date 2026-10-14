package delivery.job;

/**
 * Execute job outcome. {@code passed} keeps its historical meaning (final pass incl. FLAKY, HEALED and REUSED)
 * so existing portal columns stay valid; honest retry metrics ride alongside.
 * Rates are null when nothing countable ran (or for legacy callers).
 */
public record ExecuteJobResult(int passed, int todo, String message,
                               int flaky, int healed, int infraError, int configError,
                               Double firstPassRate, Double finalPassRate) {

    /** Legacy shape (dry-run, runner agent): no retry metrics known. */
    public ExecuteJobResult(int passed, int todo, String message) {
        this(passed, todo, message, 0, 0, 0, 0, null, null);
    }
}
