package delivery.mobile.runner.execute;

import delivery.mobile.result.CaseOutcomeView;
import delivery.mobile.result.MobileCaseResultView;
import delivery.mobile.result.ProofKind;
import delivery.mobile.result.ProviderKind;
import delivery.mobile.result.SetupOutcome;

import java.util.List;

/**
 * Maps runner {@link CaseResult} to FE-facing {@link MobileCaseResultView} (M2-07).
 * Does not break existing CaseResult / MobileExecuteEngineTest.
 */
public final class CaseResultViews {
    private CaseResultViews() {
    }

    public static MobileCaseResultView toView(CaseResult result) {
        return toView(result, "", null, null);
    }

    public static MobileCaseResultView toView(
            CaseResult result,
            String attemptId,
            delivery.mobile.result.ObservedAppIdentity app,
            delivery.mobile.result.ObservedDeviceIdentity device) {
        if (result == null) {
            return MobileCaseResultView.blocked("", attemptId, "MISSING_RESULT");
        }
        CaseOutcomeView outcome = CaseOutcomeView.parse(result.outcome().name());
        SetupOutcome setup = switch (result.outcome()) {
            case BLOCKED -> failFastOrDeviceLost(result.failureCode())
                    ? SetupOutcome.BLOCKED
                    : SetupOutcome.FAILED;
            case INTERRUPTED_UNCERTAIN -> SetupOutcome.UNKNOWN;
            default -> SetupOutcome.NOT_REQUIRED;
        };
        ProofKind proof = switch (result.outcome()) {
            case PASSED -> ProofKind.ASSERTION;
            case FAILED -> ProofKind.HIERARCHY;
            default -> ProofKind.NONE;
        };
        // Runner CaseResult.containsRawSecret must never propagate as true on the view.
        return new MobileCaseResultView(
                result.tcId(),
                attemptId == null ? "" : attemptId,
                outcome,
                result.failureCode(),
                setup,
                proof,
                ProviderKind.KEEL,
                "",
                0L,
                result.evidence(),
                app,
                device,
                List.of(),
                List.of(),
                false);
    }

    /** FAIL_FAST / DEVICE_LOST remainder is not a setup failure (AE-L5). */
    private static boolean failFastOrDeviceLost(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return false;
        }
        String c = failureCode.trim();
        return "FAIL_FAST".equalsIgnoreCase(c) || "DEVICE_LOST".equalsIgnoreCase(c);
    }
}
