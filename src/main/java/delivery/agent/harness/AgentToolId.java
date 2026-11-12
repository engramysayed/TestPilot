package delivery.agent.harness;

/** Tool permission buckets enforced outside the model (see {@link AgentToolRegistry}). */
public enum AgentToolId {
    READ_WORKSPACE_FILES,
    SEARCH_WORKSPACE,
    INSPECT_PROJECT_STRUCTURE,
    RUN_APPROVED_MAVEN_COMMANDS,
    MODIFY_GENERATED_SOURCES,
    READ_EXECUTION_LOGS
}
