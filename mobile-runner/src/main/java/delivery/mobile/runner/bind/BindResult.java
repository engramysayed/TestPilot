package delivery.mobile.runner.bind;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class BindResult {
    private final BindStatus status;
    private final Candidate selected;
    private final List<Candidate> ranked;
    private final boolean userFallback;
    private final String detail;

    private BindResult(BindStatus status, Candidate selected, List<Candidate> ranked,
                       boolean userFallback, String detail) {
        this.status = status;
        this.selected = selected;
        this.ranked = ranked == null ? List.of() : Collections.unmodifiableList(List.copyOf(ranked));
        this.userFallback = userFallback;
        this.detail = detail == null ? "" : detail;
    }

    public static BindResult bound(Candidate selected, List<Candidate> ranked, boolean userFallback) {
        return new BindResult(BindStatus.BOUND, selected, ranked, userFallback, "");
    }

    public static BindResult notFound(List<Candidate> ranked, boolean userFallback, String detail) {
        return new BindResult(BindStatus.NOT_FOUND, null, ranked, userFallback, detail);
    }

    public static BindResult ambiguous(List<Candidate> ranked, boolean userFallback, String detail) {
        return new BindResult(BindStatus.AMBIGUOUS, null, ranked, userFallback, detail);
    }

    public static BindResult userRejected(List<Candidate> fallbackRanked, String detail) {
        // Records that user locator failed; hierarchy fallbacks may still be present for diagnostics.
        return new BindResult(BindStatus.USER_LOCATOR_REJECTED, null, fallbackRanked, true, detail);
    }

    public static BindResult invalid(String detail) {
        return new BindResult(BindStatus.INVALID_STRATEGY, null, List.of(), false, detail);
    }

    public BindStatus status() { return status; }
    public Optional<Candidate> selected() { return Optional.ofNullable(selected); }
    public List<Candidate> ranked() { return ranked; }
    /** True when a USER locator was attempted and rejected; hierarchy may have been considered. */
    public boolean userFallback() { return userFallback; }
    public String detail() { return detail; }
    public boolean isBound() { return status == BindStatus.BOUND; }
}
