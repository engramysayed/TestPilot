package delivery.agent.harness;

import java.time.Instant;

/** Opaque provider run identity for cancel/resume (Phase 2). */
public record AgentRunHandle(
        String agentId,
        String runId,
        Instant startedAt
) {
    public AgentRunHandle {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        startedAt = startedAt == null ? Instant.now() : startedAt;
    }
}
