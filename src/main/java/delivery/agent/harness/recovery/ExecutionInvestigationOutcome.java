package delivery.agent.harness.recovery;

import delivery.agent.harness.AgentTaskStatus;

/** Harness investigation result — never implies the prove step passed. */
public record ExecutionInvestigationOutcome(
        String status,
        String taskId,
        AgentTaskStatus harnessTerminalStatus,
        String summary,
        String detail) {

    public static final String SKIPPED = "SKIPPED";
    public static final String INVESTIGATED = "INVESTIGATED";
    public static final String HARNESS_FAILED = "HARNESS_FAILED";
    public static final String DUPLICATE = "DUPLICATE";

    public static ExecutionInvestigationOutcome skipped(String reason) {
        return new ExecutionInvestigationOutcome(SKIPPED, "", null, "", reason == null ? "" : reason);
    }

    public static ExecutionInvestigationOutcome duplicate() {
        return new ExecutionInvestigationOutcome(DUPLICATE, "", null, "", "already investigated this boundary");
    }

    public static ExecutionInvestigationOutcome investigated(String taskId, AgentTaskStatus status, String summary) {
        return new ExecutionInvestigationOutcome(INVESTIGATED, taskId == null ? "" : taskId, status,
                summary == null ? "" : summary, "");
    }

    public static ExecutionInvestigationOutcome harnessFailed(String detail) {
        return new ExecutionInvestigationOutcome(HARNESS_FAILED, "", null, "", detail == null ? "" : detail);
    }

    public boolean investigationRan() {
        return INVESTIGATED.equals(status);
    }
}