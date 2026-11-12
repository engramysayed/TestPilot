package delivery.agent.harness;

/** Bounded agent harness failures (distinct from parent job business errors). */
public class AgentHarnessException extends RuntimeException {
    public AgentHarnessException(String message) {
        super(message);
    }

    public AgentHarnessException(String message, Throwable cause) {
        super(message, cause);
    }
}
