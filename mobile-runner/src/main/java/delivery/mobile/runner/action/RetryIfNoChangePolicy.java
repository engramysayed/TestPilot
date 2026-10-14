package delivery.mobile.runner.action;

import delivery.mobile.runner.settle.ActionOutcome;
import delivery.mobile.runner.settle.SettleActionKind;

/**
 * Re-tap only when step declares idempotent {@code retryIfNoChange=true}, at most once,
 * and never for mutating-uncertain steps (arch §6). Mirrors MobileStep IR validation at runtime.
 */
public final class RetryIfNoChangePolicy {
    public enum Decision { ALLOW_ONCE, REJECT, NO_RETRY }

    private RetryIfNoChangePolicy() {}

    public static Decision evaluate(
            boolean retryIfNoChange,
            SettleActionKind kind,
            boolean mutatingUncertain,
            ActionOutcome outcome,
            boolean alreadyRetried
    ) {
        if (!retryIfNoChange) return Decision.NO_RETRY;
        if (kind != SettleActionKind.TAP && kind != SettleActionKind.LONG_PRESS) {
            return Decision.REJECT;
        }
        if (mutatingUncertain) return Decision.REJECT;
        if (outcome != ActionOutcome.NO_CHANGE) return Decision.NO_RETRY;
        if (alreadyRetried) return Decision.NO_RETRY;
        return Decision.ALLOW_ONCE;
    }

    public static void requireLegal(boolean retryIfNoChange, SettleActionKind kind, boolean secretOrType) {
        if (!retryIfNoChange) return;
        if (secretOrType || (kind != SettleActionKind.TAP && kind != SettleActionKind.LONG_PRESS)) {
            throw new IllegalArgumentException(
                    "retryIfNoChange rejected on mutating/TYPE/SECRET step kind=" + kind);
        }
    }
}
