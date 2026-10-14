package delivery.job;

import delivery.ir.TcDraftStatus;

/**
 * Honest per-TC verdict layered on top of {@link TcDraftStatus} (IR status enum is unchanged for
 * backward compatibility; codegen still keys off PASSED).
 */
public enum RetryVerdict {
    /** Passed on the first attempt of every intent, no heal (infra-only retries allowed). */
    PASSED,
    /** Passed, but at least one intent needed a TEST-category retry. Wins over HEALED. */
    FLAKY,
    /** Passed with no test retry, but a heal tier made a step pass on the same attempt. */
    HEALED,
    /** Failed (TODO/PARTIAL) for a test-category reason. */
    FAILED,
    /** Failed because the environment failed; excluded from pass-rate denominators. */
    INFRA_ERROR,
    /** Failed on login / credentials / environment configuration; excluded from pass-rate denominators. */
    CONFIG_ERROR,
    /** Reused from a previous run; not executed this run. */
    REUSED,
    /** PASSED row written before retry metrics existed — first-pass unknown. */
    LEGACY_PASSED;

    public static RetryVerdict of(TcDraftStatus status, int testFailuresBeforePass, String failureReason) {
        return of(status, testFailuresBeforePass, null, failureReason);
    }

    public static RetryVerdict of(TcDraftStatus status, int testFailuresBeforePass, String healTier,
                                  String failureReason) {
        if (status == null) {
            return FAILED;
        }
        return switch (status) {
            case PASSED -> testFailuresBeforePass > 0 ? FLAKY : healed(healTier) ? HEALED : PASSED;
            case REUSED -> REUSED;
            case TODO, PARTIAL -> switch (RetryCategory.classify(failureReason)) {
                case INFRA -> INFRA_ERROR;
                case CONFIG -> CONFIG_ERROR;
                default -> FAILED;
            };
        };
    }

    static boolean healed(String healTier) {
        return healTier != null && !healTier.isBlank() && !"none".equalsIgnoreCase(healTier.trim());
    }

    public boolean finalPass() {
        return this == PASSED || this == FLAKY || this == HEALED || this == LEGACY_PASSED;
    }

    /** CSS-friendly badge key used by the portal (keel-verdict--{key}). */
    public String badgeKey() {
        return switch (this) {
            case INFRA_ERROR -> "infra";
            case CONFIG_ERROR -> "config";
            case LEGACY_PASSED -> "legacy";
            default -> name().toLowerCase(java.util.Locale.ROOT);
        };
    }
}
