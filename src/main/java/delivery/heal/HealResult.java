package delivery.heal;

import delivery.codegen.ProvenStep;

import java.util.List;

/**
 * Outcome of one heal attempt. Successful tiers are retry, ollama, vision, cursor, invent, or recovery.
 */
public record HealResult(
        boolean ok,
        List<ProvenStep> steps,
        String tierUsed,
        String reason,
        List<String> automationNotes,
        String recoveryThought
) {
    public HealResult {
        steps = steps == null ? List.of() : steps;
        automationNotes = automationNotes == null ? List.of() : List.copyOf(automationNotes);
        recoveryThought = recoveryThought == null ? "" : recoveryThought;
    }

    public static HealResult success(List<ProvenStep> steps, String tierUsed) {
        return new HealResult(true, steps == null ? List.of() : steps, tierUsed, "", List.of(), "");
    }

    public static HealResult recovery(List<ProvenStep> steps, List<String> automationNotes, String thought) {
        return new HealResult(
                true,
                steps == null ? List.of() : steps,
                "recovery",
                "",
                automationNotes == null ? List.of() : automationNotes,
                thought == null ? "" : thought);
    }

    public static HealResult fail(String reason) {
        return new HealResult(false, List.of(), "", reason == null ? "" : reason, List.of(), "");
    }
}
