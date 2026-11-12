package delivery.agent.harness;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/** Durable agent task delegated by a Keel workflow. */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        AgentProviderId providerId,
        String inputFingerprint,
        String stageKey,
        int schemaVersion
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
        inputFingerprint = inputFingerprint == null ? "" : inputFingerprint;
        stageKey = stageKey == null ? "" : stageKey;
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
    }

    public AgentTask withStatus(AgentTaskStatus status) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, status, objective, contextReferences, workspacePath,
                createdAt, startedAt, completedAt, agentId, agentRunId, attempt, budget, resultReference, providerId,
                inputFingerprint, stageKey, schemaVersion);
    }

    public AgentTask withRun(AgentRunHandle handle) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, taskStatus, objective, contextReferences, workspacePath,
                createdAt, handle.startedAt(), completedAt, handle.agentId(), handle.runId(), attempt, budget,
                resultReference, providerId, inputFingerprint, stageKey, schemaVersion);
    }

    public AgentTask withCompleted(Instant at, String resultRef) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, taskStatus, objective, contextReferences, workspacePath,
                createdAt, startedAt, at, agentId, agentRunId, attempt, budget, resultRef, providerId,
                inputFingerprint, stageKey, schemaVersion);
    }

    public AgentTask withWorkspacePath(String path) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, taskStatus, objective, contextReferences, path,
                createdAt, startedAt, completedAt, agentId, agentRunId, attempt, budget, resultReference, providerId,
                inputFingerprint, stageKey, schemaVersion);
    }

    public AgentTask withPolicy(AgentPolicy policy) {
        return new AgentTask(
                taskId, parentJobId, projectId, taskType, taskStatus, objective, contextReferences, workspacePath,
                createdAt, startedAt, completedAt, agentId, agentRunId, attempt, policy, resultReference, providerId,
                inputFingerprint, stageKey, schemaVersion);
    }
}
