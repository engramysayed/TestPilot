package delivery.agent.harness;

import java.time.Instant;
import java.util.List;

/** Durable agent task delegated by a Keel workflow. */
public record AgentTask(
        String taskId,
        String parentJobId,
        String projectId,
        AgentTaskType taskType,
        AgentTaskStatus taskStatus,
        String objective,
        List<AgentContextReference> contextReferences,
        String workspacePath,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String agentId,
        String agentRunId,
        int attempt,
        AgentPolicy budget,
        String resultReference,
        AgentProviderId providerId
) {
    public AgentTask {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (parentJobId == null || parentJobId.isBlank()) {
            throw new IllegalArgumentException("parentJobId is required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        if (taskType == null) {
            throw new IllegalArgumentException("taskType is required");
        }
        if (taskStatus == null) {
            throw new IllegalArgumentException("taskStatus is required");
        }
        objective = objective == null ? "" : objective;
        contextReferences = contextReferences == null ? List.of() : List.copyOf(contextReferences);
        workspacePath = workspacePath == null ? "" : workspacePath;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        attempt = Math.max(1, attempt);
        budget = budget == null ? AgentPolicy.disabled() : budget;
        resultReference = resultReference == null ? "" : resultReference;
        providerId = providerId == null ? AgentProviderId.FAKE : providerId;
    }

    public AgentTask withStatus(AgentTaskStatus status) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, status, objective, contextReferences, workspacePath,
                createdAt, startedAt, completedAt, agentId, agentRunId, attempt, budget, resultReference, providerId);
    }

    public AgentTask withRun(AgentRunHandle handle) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, taskStatus, objective, contextReferences, workspacePath,
                createdAt, handle.startedAt(), completedAt, handle.agentId(), handle.runId(), attempt, budget,
                resultReference, providerId);
    }

    public AgentTask withCompleted(Instant at, String resultRef) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, taskStatus, objective, contextReferences, workspacePath,
                createdAt, startedAt, at, agentId, agentRunId, attempt, budget, resultRef, providerId);
    }
}
