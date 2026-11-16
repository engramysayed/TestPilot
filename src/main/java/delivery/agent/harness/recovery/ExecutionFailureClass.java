package delivery.agent.harness.recovery;

/** Describes failure nature for investigation; separate from escalate/no-escalate decision. */
public enum ExecutionFailureClass {
    LOCATOR_RESOLUTION,
    ELEMENT_INTERACTION,
    SYNCHRONIZATION_TIMEOUT,
    UNEXPECTED_APPLICATION_STATE,
    ASSERTION_OR_EXPECTED_BEHAVIOR,
    BROWSER_OR_INFRASTRUCTURE,
    UNKNOWN
}