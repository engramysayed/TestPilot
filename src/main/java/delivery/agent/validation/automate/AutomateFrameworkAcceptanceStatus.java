package delivery.agent.validation.automate;

/** Automate framework acceptance outcomes — harness completion is never proof of acceptance. */
public enum AutomateFrameworkAcceptanceStatus {
    ACCEPTED,
    CONTRACT_PASSED,
    MANDATORY_VIOLATIONS,
    REPAIR_NOT_ENABLED,
    REPAIR_UNAVAILABLE,
    REPAIR_FAILED,
    REPAIR_INTERRUPTED,
    COMPILE_FAILED,
    RECONCILIATION_FAILED,
    REVALIDATION_FAILED,
    /** B4 closed: FrameworkRepairArchitectureAssessor reported functional gaps. */
    ARCHITECTURE_GAPS
}