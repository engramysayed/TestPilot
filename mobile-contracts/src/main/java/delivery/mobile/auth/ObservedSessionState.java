package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Result of evaluating session / signed-out / account predicates (M3-02).
 * No secrets.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObservedSessionState(
        SessionCheckOutcome outcome,
        SessionDetailCode detail,
        String observedAccountId,
        boolean strongIdentityEvidence,
        String message
) {
    public ObservedSessionState {
        outcome = outcome == null ? SessionCheckOutcome.UNKNOWN_ERROR : outcome;
        detail = detail == null ? SessionDetailCode.NONE : detail;
        observedAccountId = observedAccountId == null ? "" : observedAccountId.trim();
        message = message == null ? "" : message;
        if (outcome == SessionCheckOutcome.UNKNOWN_ERROR) {
            strongIdentityEvidence = false;
        }
    }

    public static ObservedSessionState signedInAsRequested(String accountId, boolean strong) {
        return new ObservedSessionState(
                SessionCheckOutcome.SIGNED_IN_AS_REQUESTED,
                strong ? SessionDetailCode.CACHED_VALID : SessionDetailCode.IDENTITY_UNVERIFIED,
                accountId,
                strong,
                strong ? "signed-in-as-requested" : "signed-in with weaker identity evidence");
    }

    public static ObservedSessionState signedOut() {
        return new ObservedSessionState(
                SessionCheckOutcome.SIGNED_OUT_OR_DIFFERENT_ACCOUNT,
                SessionDetailCode.SIGNED_OUT, "", false, "signed-out");
    }

    public static ObservedSessionState expired() {
        return new ObservedSessionState(
                SessionCheckOutcome.SIGNED_OUT_OR_DIFFERENT_ACCOUNT,
                SessionDetailCode.EXPIRED, "", false, "session expired");
    }

    public static ObservedSessionState wrongAccount(String observedAccountId) {
        return new ObservedSessionState(
                SessionCheckOutcome.SIGNED_OUT_OR_DIFFERENT_ACCOUNT,
                SessionDetailCode.WRONG_ACCOUNT,
                observedAccountId == null ? "" : observedAccountId,
                true,
                "different account");
    }

    public static ObservedSessionState unknown(String message) {
        return new ObservedSessionState(
                SessionCheckOutcome.UNKNOWN_ERROR,
                SessionDetailCode.PROBE_ERROR, "", false,
                message == null ? "session check unknown" : message);
    }
}
