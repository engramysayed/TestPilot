package delivery.agent.harness;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Execution limits for one agent task — permissions are never agent-chosen. */
public record AgentPolicy(
        Duration maxRuntime,
        int maxAttempts,
        Set<AgentToolId> allowedTools,
        List<String> allowedWorkspaceRoots,
        boolean networkAllowed,
        boolean requireHumanApproval
) {
    public AgentPolicy {
        maxRuntime = maxRuntime == null || maxRuntime.isNegative() || maxRuntime.isZero()
                ? Duration.ofMinutes(15)
                : maxRuntime;
        maxAttempts = Math.max(1, maxAttempts);
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        allowedWorkspaceRoots = allowedWorkspaceRoots == null ? List.of() : List.copyOf(allowedWorkspaceRoots);
    }

    public static AgentPolicy disabled() {
        return new AgentPolicy(Duration.ofMinutes(15), 1, Set.of(), List.of(), false, false);
    }

    public boolean isWorkspaceAllowed(Path workspace) {
        if (workspace == null) {
            return false;
        }
        Path normalized = workspace.toAbsolutePath().normalize();
        if (allowedWorkspaceRoots.isEmpty()) {
            return true;
        }
        for (String root : allowedWorkspaceRoots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path allowed = Path.of(root).toAbsolutePath().normalize();
            if (normalized.equals(allowed) || normalized.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }
}
