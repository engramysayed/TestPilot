package delivery.agent.harness;

import java.util.EnumSet;
import java.util.Set;

/** Default tool sets per task type plus policy checks. */
public final class AgentToolRegistry {
    private static final Set<AgentToolId> READ_ONLY = EnumSet.of(
            AgentToolId.READ_WORKSPACE_FILES,
            AgentToolId.SEARCH_WORKSPACE,
            AgentToolId.INSPECT_PROJECT_STRUCTURE,
            AgentToolId.READ_EXECUTION_LOGS);

    private static final Set<AgentToolId> VALIDATION_REPAIR = EnumSet.of(
            AgentToolId.READ_WORKSPACE_FILES,
            AgentToolId.SEARCH_WORKSPACE,
            AgentToolId.INSPECT_PROJECT_STRUCTURE,
            AgentToolId.RUN_APPROVED_MAVEN_COMMANDS,
            AgentToolId.MODIFY_GENERATED_SOURCES,
            AgentToolId.READ_EXECUTION_LOGS);

    private AgentToolRegistry() {
    }

    public static Set<AgentToolId> defaultsFor(AgentTaskType type) {
        if (type == null) {
            return Set.copyOf(READ_ONLY);
        }
        return switch (type) {
            case FRAMEWORK_VALIDATION_V1, FRAMEWORK_ADAPTATION_V1 -> Set.copyOf(VALIDATION_REPAIR);
            case EXECUTION_FAILURE_INVESTIGATION_V1, GENERATED_TEST_REVIEW_V1, BUG_INVESTIGATION_V1 ->
                    Set.copyOf(READ_ONLY);
        };
    }

    public static void requireAllowed(AgentPolicy policy, AgentToolId tool) {
        if (policy == null) {
            throw new AgentHarnessException("policy is required");
        }
        if (tool == null) {
            throw new AgentHarnessException("tool is required");
        }
        if (!policy.allowedTools().contains(tool)) {
            throw new AgentHarnessException("tool forbidden by policy: " + tool);
        }
    }
}
