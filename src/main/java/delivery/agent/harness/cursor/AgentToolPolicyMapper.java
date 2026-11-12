package delivery.agent.harness.cursor;

import delivery.agent.harness.AgentPolicy;
import delivery.agent.harness.AgentToolId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maps Keel {@link AgentToolId} set to Cursor SDK tool names for the sidecar. */
public final class AgentToolPolicyMapper {
    private static final List<String> ALWAYS_DISALLOWED = List.of(
            "shell", "mcp", "task", "webFetch", "webSearch", "delete");

    private AgentToolPolicyMapper() {
    }

    public static MappedTools map(AgentPolicy policy) {
        Set<String> allow = new LinkedHashSet<>();
        if (policy != null) {
            for (AgentToolId id : policy.allowedTools()) {
                switch (id) {
                    case READ_WORKSPACE_FILES -> allow.addAll(List.of("read", "grep", "glob", "ls"));
                    case SEARCH_WORKSPACE -> allow.addAll(List.of("grep", "glob", "semSearch"));
                    case INSPECT_PROJECT_STRUCTURE -> allow.addAll(List.of("ls", "glob"));
                    case MODIFY_GENERATED_SOURCES -> allow.add("edit");
                    case RUN_APPROVED_MAVEN_COMMANDS -> { /* shell denied in Phase 2 */ }
                    case READ_EXECUTION_LOGS -> allow.addAll(List.of("read", "grep"));
                    default -> { }
                }
            }
        }
        if (allow.isEmpty()) {
            allow.add("read");
        }
        List<String> disallow = new ArrayList<>(ALWAYS_DISALLOWED);
        return new MappedTools(List.copyOf(allow), List.copyOf(disallow));
    }

    public record MappedTools(List<String> tools, List<String> disallowedTools) {
    }
}
