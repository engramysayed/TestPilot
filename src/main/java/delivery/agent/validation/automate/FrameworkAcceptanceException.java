package delivery.agent.validation.automate;

/** Raised when Automate cannot publish a framework candidate (mandatory contract or repair failure). */
public final class FrameworkAcceptanceException extends IllegalStateException {
    private final AutomateFrameworkAcceptanceOutcome outcome;

    public FrameworkAcceptanceException(AutomateFrameworkAcceptanceOutcome outcome) {
        super(summary(outcome));
        this.outcome = outcome;
    }

    public AutomateFrameworkAcceptanceOutcome outcome() {
        return outcome;
    }

    private static String summary(AutomateFrameworkAcceptanceOutcome outcome) {
        if (outcome == null) {
            return "FRAMEWORK_ACCEPTANCE_FAILED";
        }
        return "FRAMEWORK_ACCEPTANCE_" + outcome.status() + ": " + outcome.detail();
    }
}