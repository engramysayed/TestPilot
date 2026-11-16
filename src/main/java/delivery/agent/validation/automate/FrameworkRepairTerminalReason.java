package delivery.agent.validation.automate;

/** Operator-facing terminal categories — do not alter harness task statuses. */
public enum FrameworkRepairTerminalReason {
    SUCCESS,
    BRIDGE_WAIT_TIMEOUT,
    SIDECAR_START_TIMEOUT,
    CURSOR_RUN_FAILURE,
    RECONCILE_POLL_EXHAUSTED,
    EXPLICIT_JOB_CANCEL,
    RECONCILIATION_FAILURE,
    COMPILE_FAILURE,
    REVALIDATION_FAILURE,
    ARCHITECTURE_QUALITY_FAILURE,
    PROMOTION_FAILURE,
    REPAIR_NOT_ENABLED,
    REPAIR_UNAVAILABLE,
    REPAIR_FAILED,
    REPAIR_INTERRUPTED,
    UNSPECIFIED
}