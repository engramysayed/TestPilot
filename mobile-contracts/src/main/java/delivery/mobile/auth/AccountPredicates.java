package delivery.mobile.auth;

/**
 * Evaluates protected signed-in / signed-out predicates against observed facts (M3-02).
 * Missing identity node ≠ empty text; unknown probe errors never mean proceed.
 */
public final class AccountPredicates {
    private AccountPredicates() {
    }

    /**
     * @param sessionPredicatesMatched all/any session predicates as authored (caller resolves semantics)
     * @param signedOutPredicatesMatched signed-out predicate list result
     * @param observedAccountId observable account id when present; blank if not observable
     * @param probeError true when the check itself failed / uncertain
     */
    public static ObservedSessionState evaluate(
            LogicalAccountRef requested,
            boolean sessionPredicatesMatched,
            boolean signedOutPredicatesMatched,
            String observedAccountId,
            boolean probeError) {
        if (probeError) {
            return ObservedSessionState.unknown("session probe error");
        }
        String observed = observedAccountId == null ? "" : observedAccountId.trim();
        String want = requested == null ? "" : requested.accountId();

        if (signedOutPredicatesMatched && !sessionPredicatesMatched) {
            return ObservedSessionState.signedOut();
        }
        if (sessionPredicatesMatched) {
            if (!want.isEmpty() && !observed.isEmpty() && !want.equals(observed)) {
                return ObservedSessionState.wrongAccount(observed);
            }
            if (!want.isEmpty() && observed.isEmpty()) {
                // Cannot distinguish account — weaker evidence, do not claim known account.
                return ObservedSessionState.signedInAsRequested("", false);
            }
            return ObservedSessionState.signedInAsRequested(
                    observed.isEmpty() ? want : observed, true);
        }
        if (!sessionPredicatesMatched && !signedOutPredicatesMatched) {
            // Neither signed-in nor signed-out observable → uncertain
            return ObservedSessionState.unknown("neither session nor signed-out predicates matched");
        }
        return ObservedSessionState.signedOut();
    }
}
