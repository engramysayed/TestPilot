package project.utils.settle;

/**
 * A required action (A8 rule effect=required) produced no observable effect: no DOM, URL or
 * visual change within the rule's effectWaitMs. Thrown by default, matching the engine (NO_EFFECT is
 * never PASS). {@code -Dkeel.noeffect=warn} downgrades it to the old WARN + Allure step.
 */
public class NoEffectError extends AssertionError {
    private final String action;

    public NoEffectError(String action, String message) {
        super(message);
        this.action = action;
    }

    public String action() {
        return action;
    }
}
