package delivery.agent.harness;

/**
 * Provider abstraction for agent execution. Phase 1 ships {@link FakeAgentRuntime} only.
 */
public interface AgentRuntime {

    AgentProviderId providerId();

    AgentRunHandle start(AgentTask task);

    AgentRunHandle resume(String agentId, String runId);

    void cancel(String agentId, String runId);

    AgentRunResult getResult(String agentId, String runId);
}
