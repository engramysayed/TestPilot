package delivery.mobile.runner.precision;

/**
 * One mobile Precision choice. Fallback to Keel is recorded and is not counted as a Cursor proof.
 * The response may name a candidate id. It may not contain code or a secret.
 */
public final class MobilePrecisionGate {
    static final String SECRET_CANARY = "SECRET_CANARY_OTP_999888";

    private MobilePrecisionGate() {}

    public static PrecisionOutcome rank(PrecisionInput input) {
        String response = input.responseText();
        if (response.contains(SECRET_CANARY)) {
            return PrecisionOutcome.rejected("SECRET_REDACTION");
        }
        if (looksLikeCode(response)) {
            return PrecisionOutcome.rejected("CODE_REJECTED");
        }
        if (input.batch()) {
            return PrecisionOutcome.rejected("BATCH_REJECTED");
        }
        if (!input.providerAvailable()) {
            return PrecisionOutcome.fallback("UNAVAILABLE");
        }
        if (!input.providerAllowed()) {
            return PrecisionOutcome.fallback("DISALLOWED");
        }
        if (input.callsUsed() >= input.callCap()) {
            return PrecisionOutcome.fallback("EXHAUSTED");
        }
        PrecisionCandidate chosen = null;
        PrecisionCandidate override = null;
        for (PrecisionCandidate c : input.candidates()) {
            if (c.userOverride()) {
                override = c;
            }
            if (c.id().equals(input.chosenId())) {
                chosen = c;
            }
        }
        if (chosen == null) {
            return PrecisionOutcome.rejected("INVALID_ID");
        }
        if (!chosen.contextKind().equals(input.stepContext())) {
            return PrecisionOutcome.rejected("WRONG_CONTEXT");
        }
        if (override != null && !override.id().equals(chosen.id())) {
            return PrecisionOutcome.rejected("USER_OVERRIDE");
        }
        return PrecisionOutcome.chosen(chosen.id(), input.providerName());
    }

    private static boolean looksLikeCode(String response) {
        String s = response.toLowerCase();
        return s.contains("runtime.getruntime()")
                || s.contains(".exec(")
                || s.contains("mobile: shell")
                || s.contains("```");
    }
}
