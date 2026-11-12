package delivery.agent.harness;

import java.util.List;
import java.util.Map;

/** Selects a small, purposeful context package for an agent run. */
public final class AgentContextBuilder {

    public AgentContextManifest build(AgentTask task, List<String> acceptanceCriteria, Map<String, String> metadata) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        return new AgentContextManifest(
                task.taskId(),
                task.parentJobId(),
                task.objective(),
                acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria),
                task.contextReferences(),
                metadata == null ? Map.of() : Map.copyOf(metadata));
    }
}
