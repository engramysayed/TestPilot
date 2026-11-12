package delivery.agent.harness;

import java.util.List;
import java.util.Map;

/** Structured context package written beside the task (not sent wholesale to a provider in Phase 1). */
public record AgentContextManifest(
        String taskId,
        String parentJobId,
        String objective,
        List<String> acceptanceCriteria,
        List<AgentContextReference> references,
        Map<String, String> metadata
) {
    public AgentContextManifest {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        objective = objective == null ? "" : objective;
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        references = references == null ? List.of() : List.copyOf(references);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
