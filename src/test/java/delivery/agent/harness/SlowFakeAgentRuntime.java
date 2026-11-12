package delivery.agent.harness;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake runtime that delays {@link #getResult} for lease / worker timing tests. */
public final class SlowFakeAgentRuntime implements AgentRuntime {
    private final long delayMs;
    private final Map<String, AgentRunResult> results = new ConcurrentHashMap<>();

    public SlowFakeAgentRuntime(long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public AgentProviderId providerId() {
        return AgentProviderId.FAKE;
    }

    @Override
    public AgentRunHandle start(AgentTask task) {
        String agentId = "fake-agent-" + task.taskId();
        String runId = UUID.randomUUID().toString();
        results.put(runKey(agentId, runId), new AgentRunResult(
                AgentTaskStatus.COMPLETED,
                "Slow fake completed",
                Map.of("provider", "FAKE_SLOW"),
                java.util.List.of(),
                ""));
        return new AgentRunHandle(agentId, runId, Instant.now());
    }

    @Override
    public AgentRunHandle resume(String agentId, String runId) {
        return new AgentRunHandle(agentId, runId, Instant.now());
    }

    @Override
    public void cancel(String agentId, String runId) {
    }

    @Override
    public AgentRunResult getResult(String agentId, String runId) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentHarnessException("interrupted during slow fake");
        }
        AgentRunResult result = results.get(runKey(agentId, runId));
        if (result == null) {
            throw new AgentHarnessException("no slow fake result");
        }
        return result;
    }

    private static String runKey(String agentId, String runId) {
        return agentId + "::" + runId;
    }
}
