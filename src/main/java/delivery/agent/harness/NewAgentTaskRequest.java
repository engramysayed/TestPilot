package delivery.agent.harness;

import java.util.List;

/** Input to create a pending task under a parent Keel job. */
public record NewAgentTaskRequest(
        String parentJobId,
        String projectId,
        AgentTaskType taskType,
        String objective,
        List<AgentContextReference> contextReferences,
        String workspacePath
) {
    public NewAgentTaskRequest {
        if (parentJobId == null || parentJobId.isBlank()) {
            throw new IllegalArgumentException("parentJobId is required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        if (taskType == null) {
            throw new IllegalArgumentException("taskType is required");
        }
        objective = objective == null ? "" : objective;
        contextReferences = contextReferences == null ? List.of() : List.copyOf(contextReferences);
        workspacePath = workspacePath == null ? "" : workspacePath;
    }
}
