package delivery.agent.harness;

/**
 * Versioned agent task kinds. Free-text objectives live on {@link AgentTask}; type selects routing and policy.
 */
public enum AgentTaskType {
    /** Post-emit framework inspection and permitted repair (Phase 3). */
    FRAMEWORK_VALIDATION_V1,
    /** Read-only failure analysis after execute (Phase 4). */
    EXECUTION_FAILURE_INVESTIGATION_V1,
    /** Customer convention adaptation inside generated workspace (Phase 5). */
    FRAMEWORK_ADAPTATION_V1,
    /** Pre-run generated test review (Phase 6). */
    GENERATED_TEST_REVIEW_V1,
    /** Bug Hunter deep-dive (Phase 6). */
    BUG_INVESTIGATION_V1
}
