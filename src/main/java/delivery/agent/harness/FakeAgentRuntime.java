package delivery.agent.harness;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process runtime for Phase 1 tests. Completes synchronously with a stub success payload.
 */
public final class FakeAgentRuntime implements AgentRuntime {
    private final Map<String, AgentRunResult> results = new ConcurrentHashMap<>();
    private final Map<String, AgentTaskStatus> runStatus = new ConcurrentHashMap<>();

    @Override
    public AgentProviderId providerId() {
        return AgentProviderId.FAKE;
    }

    @Override
    public AgentRunHandle start(AgentTask task) {
        String agentId = "fake-agent-" + task.taskId();
        String runId = UUID.randomUUID().toString();
        AgentRunHandle handle = new AgentRunHandle(agentId, runId, Instant.now());
        runStatus.put(runKey(agentId, runId), AgentTaskStatus.RUNNING);
        results.put(runKey(agentId, runId), new AgentRunResult(
                AgentTaskStatus.COMPLETED,
                "Fake runtime completed",
                Map.of("status", "PASS", "provider", "FAKE"),
                java.util.List.of(),
                ""));
        return handle;
    }

    @Override
    public AgentRunHandle resume(String agentId, String runId) {
        String key = runKey(agentId, runId);
        if (!results.containsKey(key)) {
            runStatus.put(key, AgentTaskStatus.RUNNING);
            results.put(key, new AgentRunResult(
                    AgentTaskStatus.COMPLETED,
                    "Fake runtime resumed",
                    Map.of("status", "PASS", "provider", "FAKE", "resumed", true),
                    java.util.List.of(),
                    ""));
        }
        return new AgentRunHandle(agentId, runId, Instant.now());
    }

    @Override
    public void cancel(String agentId, String runId) {
        String key = runKey(agentId, runId);
        runStatus.put(key, AgentTaskStatus.CANCELLED);
        results.put(key, new AgentRunResult(
                AgentTaskStatus.CANCELLED,
                "Cancelled",
                Map.of(),
                java.util.List.of(),
                ""));
    }

    @Override
    public AgentRunResult getResult(String agentId, String runId) {
        AgentRunResult result = results.get(runKey(agentId, runId));
        if (result == null) {
            throw new AgentHarnessException("no result for fake run: " + agentId + "/" + runId);
        }
        return result;
    }

    private static String runKey(String agentId, String runId) {
        return agentId + "::" + runId;
    }
}
