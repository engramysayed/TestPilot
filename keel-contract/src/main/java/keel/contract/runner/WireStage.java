package keel.contract.runner;

/** Wire form of the durable claim stage. Names must match DurableJobClaim.Stage 1:1. */
public enum WireStage {
    ADMITTED,
    BROWSER,
    /** M1-05: device session active (uncertain-outcome semantics like BROWSER). */
    DEVICE_ACTIVE,
    /** M1-05: mobile case execution in progress (uncertain-outcome semantics like BROWSER). */
    MOBILE_EXECUTING,
    EMIT,
    PUBLISH
}
