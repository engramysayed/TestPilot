package delivery.agent.harness;

/** Routing outcome: provider + policy for a task type (no business decisions). */
public record AgentTaskPlan(
        AgentTaskType taskType,
        AgentProviderId providerId,
        AgentPolicy policy
) {
    public AgentTaskPlan {
        if (taskType == null) {
            throw new IllegalArgumentException("taskType is required");
        }
        if (providerId == null) {
            throw new IllegalArgumentException("providerId is required");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
    }
}
