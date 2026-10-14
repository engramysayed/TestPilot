package delivery.mobile.runner.action;

import delivery.mobile.runner.settle.ActionOutcome;
import delivery.mobile.runner.settle.SettleActionKind;
import delivery.mobile.runner.settle.SettleResult;

/**
 * Result of a native action. {@link ActionOutcome#NO_CHANGE} on TAP maps to {@code ACTION_NO_EFFECT}
 * and is never PASS evidence.
 */
public final class ActionResult {
    public enum Status {
        OK,
        ACTION_NO_EFFECT,
        ACTION_FAILED,
        TYPE_MISMATCH,
        TRANSPORT_FAILED,
        HIDDEN_OR_DISABLED,
        STALE,
        ASSERTION_FAILED,
        SCROLL_LIMIT,
        KEYBOARD_NOT_DISMISSED,
        CANCELLED,
        GEOMETRY_CHANGED
    }

    private final Status status;
    private final ActionOutcome outcome;
    private final SettleResult settle;
    private final boolean passEvidence;
    private final String detail;
    private final boolean retriedNoChange;

    public ActionResult(Status status, ActionOutcome outcome, SettleResult settle,
                        boolean passEvidence, String detail, boolean retriedNoChange) {
        this.status = status;
        this.outcome = outcome;
        this.settle = settle;
        this.passEvidence = passEvidence;
        this.detail = detail == null ? "" : detail;
        this.retriedNoChange = retriedNoChange;
    }

    public static ActionResult fromSettle(SettleActionKind kind, SettleResult settle, boolean businessAssertOk) {
        ActionOutcome o = settle.outcome();
        if (!businessAssertOk) {
            return new ActionResult(Status.ASSERTION_FAILED, o, settle, false, "business assertion failed", false);
        }
        if (o == ActionOutcome.NO_CHANGE && (kind == SettleActionKind.TAP || kind == SettleActionKind.LONG_PRESS)) {
            return new ActionResult(Status.ACTION_NO_EFFECT, o, settle, false, "tap had no observable effect", false);
        }
        return new ActionResult(Status.OK, o, settle, true, "", false);
    }

    public Status status() { return status; }
    public ActionOutcome outcome() { return outcome; }
    public SettleResult settle() { return settle; }
    public boolean passEvidence() { return passEvidence; }
    public String detail() { return detail; }
    public boolean retriedNoChange() { return retriedNoChange; }

    public ActionResult withRetried(boolean v) {
        return new ActionResult(status, outcome, settle, passEvidence, detail, v);
    }

    public ActionResult withDetail(String d) {
        return new ActionResult(status, outcome, settle, passEvidence, d, retriedNoChange);
    }
}
