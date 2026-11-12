package delivery.agent.harness;

import delivery.portal.AgentHarnessProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps task types to provider id and {@link AgentPolicy}. CURSOR is never selected while harness is disabled.
 */
public final class AgentTaskRouter {
    private final AgentHarnessProperties properties;

    public AgentTaskRouter(AgentHarnessProperties properties) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
    }

    public AgentTaskPlan plan(AgentTaskType taskType, Path workspaceRoot) {
        if (taskType == null) {
            throw new IllegalArgumentException("taskType is required");
        }
        AgentProviderId provider = properties.isEnabled() && properties.isCursorEnabled()
                ? AgentProviderId.CURSOR
                : AgentProviderId.FAKE;
        // Phase 1: never start CURSOR even when flagged — no runtime bean wired yet.
        provider = AgentProviderId.FAKE;

        Duration maxRuntime = Duration.ofMinutes(Math.max(1, properties.getMaxRuntimeMinutes()));
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        List<String> roots = new ArrayList<>();
        if (workspaceRoot != null) {
            roots.add(workspaceRoot.toAbsolutePath().normalize().toString());
        }

        AgentPolicy policy = new AgentPolicy(
                maxRuntime,
                maxAttempts,
                AgentToolRegistry.defaultsFor(taskType),
                List.copyOf(roots),
                false,
                false);

        return new AgentTaskPlan(taskType, provider, policy);
    }
}
