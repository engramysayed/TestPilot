package delivery.agent.harness;

/** Durable agent task lifecycle (orthogonal to parent Keel job business outcome). */
public enum AgentTaskStatus {
    PENDING,
    STARTING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    INTERRUPTED
}
