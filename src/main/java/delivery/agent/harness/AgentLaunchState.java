package delivery.agent.harness;

import java.time.Instant;

/** Java-owned launch metadata persisted as {@code launch-state.json}. */
public record AgentLaunchState(
        String launchState,
        String agentId,
        String runId,
        Long sidecarPid,
        Instant cancelRequestedAt,
        String idempotencyKey,
        int attempt
) {
    public AgentLaunchState {
        launchState = launchState == null ? "CREATED" : launchState;
        agentId = agentId == null ? "" : agentId;
        runId = runId == null ? "" : runId;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        attempt = Math.max(1, attempt);
    }
}
